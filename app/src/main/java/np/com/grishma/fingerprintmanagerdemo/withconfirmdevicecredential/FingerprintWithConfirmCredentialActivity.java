package np.com.grishma.fingerprintmanagerdemo.withconfirmdevicecredential;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import np.com.grishma.fingerprintmanagerdemo.R;

/**
 * An activity to demonstrate {@link KeyguardManager}'s createConfirmDeviceCredentialIntent
 * The intent opens as an activityForResult
 * The intent provides various method (PIN, Pattern, Fingerprint or Password) if the user want to authenticate
 */
public class FingerprintWithConfirmCredentialActivity extends AppCompatActivity {

    private static final String KEY_NAME = "fingerprint_manager_intent_key"; // Alias for our key in the AndroidKeyStore
    private static final byte[] SECRET_BYTE_ARRAY = new byte[]{1, 2, 3, 4, 5, 6};
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;
    // User authentication validity duration in Seconds, for test purpose 5 seconds only
    private static final int AUTHENTICATION_DURATION_SECONDS = 5;

    @BindView(R.id.button_purchase_item)
    Button purchaseButton;

    private KeyguardManager keyguardManager;
    private KeyStore keyStore;
    private Cipher cipher;
    private SecretKey secretKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_with_confirm_credential_activity);
        ButterKnife.bind(this);

        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        // Check if the user has set up a lock screen
        if (!keyguardManager.isKeyguardSecure()) {
            // Show a message that the user hasn't set up a lock screen.
            Toast.makeText(this,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Screenlock' to set up a lock screen",
                    Toast.LENGTH_LONG).show();
            purchaseButton.setEnabled(false);
            return;
        }

        generateKey();
    }

    /**
     * Creates a symmetric key in the AndroidKeyStore which can only be used after the user has
     * authenticated with device credentials within the last N seconds.
     */
    private void generateKey() {
        // Generate a key to decrypt payment credentials, tokens, etc.
        // This will most likely be a registration step for the user when they are setting up your app.
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    // Require the user to authenticate the key before using it
                    .setUserAuthenticationRequired(true)
                    // Require that the user has unlocked in the last N seconds
                    // If the user has unlocked the device within the last this number of seconds,
                    // it can be considered as an authenticator.
                    .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException | KeyStoreException
                | CertificateException | IOException e) {
            throw new RuntimeException("Failed to create a symmetric key", e);
        }
    }

    /**
     * OnClick listener for Purchase button
     *
     * @param view {@link View} the view the activity belongs to
     */
    @OnClick(R.id.button_purchase_item)
    public void setOnClicks(View view) {
        encryptData();
    }

    /**
     * Encrypt a sample data with the generated secretKey
     *
     * @return {@link boolean} value, true if data encryption was a success, otherwise false
     */
    private boolean encryptData() {

        try {
            // get instance of AndroidKeyStore and load it
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            // fetch the secretKey from the keyStore instance
            secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);

            // create a cipher instance
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            // Attempt to encrypt the the cipher using the secretKey
            // It will only work if the user has authenticated within the last
            // AUTHENTICATION_DURATION_SECONDS
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            cipher.doFinal(SECRET_BYTE_ARRAY);

            // If the user has recently authenticated the flow will continue, else it will throw and exception
            showAlreadyAuthenticated();
            return true;

        } catch (UserNotAuthenticatedException e) {
            // User has not been authenticated, so it needs to authenticate the user
            showAuthenticationScreen();
            return false;

        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            Toast.makeText(this, "Keys are invalidated after created. Retry the purchase\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;

        } catch (BadPaddingException | IllegalBlockSizeException | KeyStoreException |
                CertificateException | UnrecoverableKeyException | IOException
                | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private void showPurchaseConfirmation() {
        Toast.makeText(FingerprintWithConfirmCredentialActivity.this, "Purchase confirmed", Toast.LENGTH_SHORT).show();
        purchaseButton.setEnabled(false);
    }

    private void showAlreadyAuthenticated() {
        Toast.makeText(FingerprintWithConfirmCredentialActivity.this, "The user was already authenticated within " + AUTHENTICATION_DURATION_SECONDS + " seconds", Toast.LENGTH_SHORT).show();
        Toast.makeText(FingerprintWithConfirmCredentialActivity.this, "Purchase successful", Toast.LENGTH_SHORT).show();
        purchaseButton.setEnabled(false);
    }

    private void showAuthenticationScreen() {
        // Create the Confirm Credentials screen. You can customize the title and description. Or
        // we will provide a generic one for you if you leave it null
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            // Challenge completed, proceed with using cipher
            if (resultCode == RESULT_OK) {
                try {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                    cipher.doFinal(SECRET_BYTE_ARRAY);
                    showPurchaseConfirmation();
                } catch (IllegalBlockSizeException | InvalidKeyException | BadPaddingException e) {
                    e.printStackTrace();
                }

            } else {
                // The user canceled or didn't complete the lock screen
                // operation. Go to error/cancellation flow.
                Toast.makeText(FingerprintWithConfirmCredentialActivity.this, "You cancelled the purchase", Toast.LENGTH_SHORT).show();
            }
        }
    }
}


