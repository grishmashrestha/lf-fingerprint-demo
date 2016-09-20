package np.com.grishma.fingerprintmanagerdemo.withdialogactivity;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v4.app.ActivityCompat;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import np.com.grishma.fingerprintmanagerdemo.R;

public class FingerprintWithDialogActivity extends AppCompatActivity {

    private static final String DIALOG_FRAGMENT_TAG = "dialogFragment";
    public static final String KEY_NAME = "fingerprint_manager_with_dialog_key";

    @BindView(R.id.button_purchase_item)
    Button purchaseButton;

    private FingerprintAuthenticationDialogFragment dialogFragment;
    private KeyStore keyStore;
    private KeyPairGenerator keyPairGenerator;
    private Signature signature;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_with_dialog);
        ButterKnife.bind(this);

        // initialize required parameters
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        FingerprintManager fingerprintManager = getSystemService(FingerprintManager.class);
        dialogFragment = new FingerprintAuthenticationDialogFragment();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // get an instance of keystore
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to get an instance of KeyStore", e);
        }

        // get an instance of keypair generator to create asymmetric key pairs, i.e. public key and private key
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to get an instance of KeyPairGenerator", e);
        }

        // get an instance of signature to be used later
        try {
            signature = Signature.getInstance("SHA256withECDSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to get an instance of Signature", e);
        }

        // check if the user has set up a fingerprint or a lock screen
        if (!keyguardManager.isKeyguardSecure()) {
            // Show a message that the user hasn't set up a fingerprint or lock screen.
            Toast.makeText(this,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Fingerprint' to set up a fingerprint",
                    Toast.LENGTH_LONG).show();
            purchaseButton.setEnabled(false);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(FingerprintWithDialogActivity.this, "Please enable fingerprint access to the app", Toast.LENGTH_SHORT).show();
            return;
        }

        // check if the device has any fingerprints enrolled
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            purchaseButton.setEnabled(false);
            // This happens when no fingerprints are registered.
            Toast.makeText(this,
                    "Go to 'Settings -> Security -> Fingerprint' and register at least one fingerprint",
                    Toast.LENGTH_LONG).show();
            return;
        }

        createKeyPair();
        purchaseButton.setEnabled(true);

    }

    @OnClick(R.id.button_purchase_item)
    public void setOnClick(View view) {
        // Set up the crypto object for later. The object will be authenticated by use
        // of the fingerprint.
        if (initSignature()) {

            // Show the fingerprint dialog. The user has the option to use the fingerprint with
            // crypto, or you can fall back to using a server-side verified password.
            dialogFragment.setCryptoObject(new FingerprintManager.CryptoObject(signature));
            boolean useFingerprintPreference = sharedPreferences
                    .getBoolean("use_fingerprint_to_authenticate_key", true);
            if (useFingerprintPreference) {
                dialogFragment.setStage(
                        FingerprintAuthenticationDialogFragment.Stage.FINGERPRINT);
            } else {
                dialogFragment.setStage(
                        FingerprintAuthenticationDialogFragment.Stage.PASSWORD);
            }
            dialogFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            // This happens if the lock screen has been disabled or or a fingerprint got
            // enrolled. Thus show the dialog to authenticate with their password first
            // and ask the user if they want to authenticate with fingerprints in the
            // future
            dialogFragment.setStage(
                    FingerprintAuthenticationDialogFragment.Stage.NEW_FINGERPRINT_ENROLLED);
            dialogFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        }
    }

    /**
     * Generates an asymmetric key pair in the Android Keystore. Every use of the private key must
     * be authorized by the user authenticating with fingerprint. Public key use is unrestricted.
     */
    public void createKeyPair() {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
        try {
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            keyPairGenerator.initialize(
                    new KeyGenParameterSpec.Builder(KEY_NAME,
                            KeyProperties.PURPOSE_SIGN)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                            // Require the user to authenticate with a fingerprint to authorize
                            // every use of the private key
                            .setUserAuthenticationRequired(true)
                            .build());
            keyPairGenerator.generateKeyPair();
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the {@link Signature} instance with the created key in the
     * {@link #createKeyPair()} method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private boolean initSignature() {
        try {
            keyStore.load(null);
            PrivateKey key = (PrivateKey) keyStore.getKey(KEY_NAME, null);
            signature.initSign(key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    public void onPurchased(byte[] signature) {
        showConfirmation(signature);
    }

    // Show confirmation, if fingerprint was used show crypto information.
    private void showConfirmation(byte[] encrypted) {
        Toast.makeText(FingerprintWithDialogActivity.this, "Purchase Successful", Toast.LENGTH_SHORT).show();
        if (encrypted != null) {
            Toast.makeText(FingerprintWithDialogActivity.this, "Encrypted key:"
                            + Base64.encodeToString(encrypted, 0),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onPurchaseFailed() {
        // Show confirmation, if fingerprint was used show crypto information.
        Toast.makeText(this, "Purchase Failed", Toast.LENGTH_SHORT).show();
    }
}

