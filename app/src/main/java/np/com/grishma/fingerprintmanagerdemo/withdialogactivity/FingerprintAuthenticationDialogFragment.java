package np.com.grishma.fingerprintmanagerdemo.withdialogactivity;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import np.com.grishma.fingerprintmanagerdemo.R;
import np.com.grishma.fingerprintmanagerdemo.withdialogactivity.server.StoreBackend;
import np.com.grishma.fingerprintmanagerdemo.withdialogactivity.server.StoreBackendImpl;
import np.com.grishma.fingerprintmanagerdemo.withdialogactivity.server.Transaction;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
public class FingerprintAuthenticationDialogFragment extends DialogFragment
        implements TextView.OnEditorActionListener, FingerprintUiHelper.Callback {

    private Button cancelButton;
    private Button secondDialogButton;
    private View fingerprintContent;
    private View backupContent;
    private EditText password;
    private CheckBox useFingerprintFutureCheckBox;
    private TextView passwordDescriptionTextView;
    private TextView newFingerprintEnrolledTextView;
    private Stage stage = Stage.FINGERPRINT;
    private FingerprintManager.CryptoObject cryptoObject;
    private FingerprintUiHelper fingerprintUiHelper;
    private FingerprintWithDialogActivity activity;
    private FingerprintUiHelper.FingerprintUiHelperBuilder fingerprintUiHelperBuilder;
    private InputMethodManager inputMethodManager;
    private SharedPreferences sharedPreferences;
    private StoreBackend storeBackend;

    public FingerprintAuthenticationDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);

        // We register a new user account here. Real apps should do this with proper UIs.

        fingerprintUiHelperBuilder = new FingerprintUiHelper.FingerprintUiHelperBuilder(getContext().getSystemService(FingerprintManager.class));
        inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        storeBackend = new StoreBackendImpl();

        enroll();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.sign_in));
        View v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false);
        cancelButton = (Button) v.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        secondDialogButton = (Button) v.findViewById(R.id.second_dialog_button);
        secondDialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (stage == Stage.FINGERPRINT) {
                    goToBackup();
                } else {
                    verifyPassword();
                }
            }
        });
        fingerprintContent = v.findViewById(R.id.fingerprint_container);
        backupContent = v.findViewById(R.id.backup_container);
        password = (EditText) v.findViewById(R.id.password);
        password.setOnEditorActionListener(this);
        passwordDescriptionTextView = (TextView) v.findViewById(R.id.password_description);
        useFingerprintFutureCheckBox = (CheckBox)
                v.findViewById(R.id.use_fingerprint_in_future_check);
        newFingerprintEnrolledTextView = (TextView)
                v.findViewById(R.id.new_fingerprint_enrolled_description);
        fingerprintUiHelper = fingerprintUiHelperBuilder.build(
                (ImageView) v.findViewById(R.id.fingerprint_icon),
                (TextView) v.findViewById(R.id.fingerprint_status), this);
        updateStage();

        // If fingerprint authentication is not available, switch immediately to the backup
        // (password) screen.
        if (!fingerprintUiHelper.isFingerprintAuthAvailable()) {
            goToBackup();
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (stage == Stage.FINGERPRINT) {
            fingerprintUiHelper.startListening(cryptoObject);
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void onPause() {
        super.onPause();
        fingerprintUiHelper.stopListening();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (FingerprintWithDialogActivity) activity;
    }

    /**
     * Sets the crypto object to be passed in when authenticating with fingerprint.
     */
    public void setCryptoObject(FingerprintManager.CryptoObject cryptoObject) {
        this.cryptoObject = cryptoObject;
    }

    /**
     * Switches to backup (password) screen. This either can happen when fingerprint is not
     * available or the user chooses to use the password authentication method by pressing the
     * button. This can also happen when the user had too many fingerprint attempts.
     */
    private void goToBackup() {
        stage = Stage.PASSWORD;
        updateStage();
        password.requestFocus();

        // Show the keyboard.
        password.postDelayed(showKeyboardRunnable, 500);

        // Fingerprint is not used anymore. Stop listening for it.
        fingerprintUiHelper.stopListening();
    }

    /**
     * Enrolls a user to the fake backend.
     */
    private void enroll() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey = keyStore.getCertificate(FingerprintWithDialogActivity.KEY_NAME).getPublicKey();
            // Provide the public key to the backend. In most cases, the key needs to be transmitted
            // to the backend over the network, for which Key.getEncoded provides a suitable wire
            // format (X.509 DER-encoded). The backend can then create a PublicKey instance from the
            // X.509 encoded form using KeyFactory.generatePublic. This conversion is also currently
            // needed on API Level 23 (Android M) due to a platform bug which prevents the use of
            // Android Keystore public keys when their private keys require user authentication.
            // This conversion creates a new public key which is not backed by Android Keystore and
            // thus is not affected by the bug.
            KeyFactory factory = KeyFactory.getInstance(publicKey.getAlgorithm());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKey.getEncoded());
            PublicKey verificationKey = factory.generatePublic(spec);
            storeBackend.enroll("user", "password", verificationKey);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException |
                IOException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether the current entered password is correct, and dismisses the the dialog and lets
     * the activity know about the result.
     */
    private void verifyPassword() {
        Transaction transaction = new Transaction("user", 1, new SecureRandom().nextLong());
        if (!storeBackend.verify(transaction, password.getText().toString())) {
            return;
        }
        if (stage == Stage.NEW_FINGERPRINT_ENROLLED) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.use_fingerprint_to_authenticate_key),
                    useFingerprintFutureCheckBox.isChecked());
            editor.apply();

            if (useFingerprintFutureCheckBox.isChecked()) {
                // Re-create the key so that fingerprints including new ones are validated.
                activity.createKeyPair();
                stage = Stage.FINGERPRINT;
            }
        }
        password.setText("");
        activity.onPurchased(null);
        dismiss();
    }

    private final Runnable showKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            inputMethodManager.showSoftInput(password, 0);
        }
    };

    private void updateStage() {
        switch (stage) {
            case FINGERPRINT:
                cancelButton.setText(R.string.cancel);
                secondDialogButton.setText(R.string.use_password);
                fingerprintContent.setVisibility(View.VISIBLE);
                backupContent.setVisibility(View.GONE);
                break;
            case NEW_FINGERPRINT_ENROLLED:
                // Intentional fall through
            case PASSWORD:
                cancelButton.setText(R.string.cancel);
                secondDialogButton.setText(R.string.ok);
                fingerprintContent.setVisibility(View.GONE);
                backupContent.setVisibility(View.VISIBLE);
                if (stage == Stage.NEW_FINGERPRINT_ENROLLED) {
                    passwordDescriptionTextView.setVisibility(View.GONE);
                    newFingerprintEnrolledTextView.setVisibility(View.VISIBLE);
                    useFingerprintFutureCheckBox.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            verifyPassword();
            return true;
        }
        return false;
    }

    @Override
    public void onAuthenticated() {
        // Callback from FingerprintUiHelper. Let the activity know that authentication was
        // successful.
        password.setText("");
        Signature signature = cryptoObject.getSignature();
        // Include a client nonce in the transaction so that the nonce is also signed by the private
        // key and the backend can verify that the same nonce can't be used to prevent replay
        // attacks.
        Transaction transaction = new Transaction("user", 1, new SecureRandom().nextLong());
        try {
            signature.update(transaction.toByteArray());
            byte[] sigBytes = signature.sign();
            if (storeBackend.verify(transaction, sigBytes)) {
                activity.onPurchased(sigBytes);
                dismiss();
            } else {
                activity.onPurchaseFailed();
                dismiss();
            }
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError() {
        goToBackup();
    }

    /**
     * Enumeration to indicate which authentication method the user is trying to authenticate with.
     */
    public enum Stage {
        FINGERPRINT,
        NEW_FINGERPRINT_ENROLLED,
        PASSWORD
    }
}
