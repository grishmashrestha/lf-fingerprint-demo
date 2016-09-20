package np.com.grishma.fingerprintmanagerdemo.withdialogactivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.support.v4.app.ActivityCompat;
import android.widget.ImageView;
import android.widget.TextView;

import np.com.grishma.fingerprintmanagerdemo.R;

/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
public class FingerprintUiHelper extends FingerprintManager.AuthenticationCallback {

    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1300;

    private FingerprintManager fingerprintManager;
    private ImageView imageView;
    private TextView errorTextView;
    private Callback callback;
    private CancellationSignal cancellationSignal;
    boolean selfCancelled;

    /**
     * Builder class for {@link FingerprintUiHelper} in which injected fields from Dagger
     * holds its fields and takes other arguments in the {@link #build} method.
     */
    public static class FingerprintUiHelperBuilder {
        private final FingerprintManager fingerprintManager;

        public FingerprintUiHelperBuilder(FingerprintManager fingerprintManager) {
            this.fingerprintManager = fingerprintManager;
        }

        public FingerprintUiHelper build(ImageView icon, TextView errorTextView, Callback callback) {
            return new FingerprintUiHelper(fingerprintManager, icon, errorTextView,
                    callback);
        }
    }

    /**
     * Constructor for {@link FingerprintUiHelper}. This method is expected to be called from
     * only the {@link FingerprintUiHelperBuilder} class.
     */
    private FingerprintUiHelper(FingerprintManager fingerprintManager,
                                ImageView icon, TextView errorTextView, Callback callback) {
        this.fingerprintManager = fingerprintManager;
        this.imageView = icon;
        this.errorTextView = errorTextView;
        this.callback = callback;
    }

    public boolean isFingerprintAuthAvailable() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
//            return false;
//        }
        return fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
    }

    public void startListening(FingerprintManager.CryptoObject cryptoObject) {
        if (!isFingerprintAuthAvailable()) {
            return;
        }
        cancellationSignal = new CancellationSignal();
        selfCancelled = false;
        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
        imageView.setImageResource(R.drawable.ic_fp_40px);
    }

    public void stopListening() {
        if (cancellationSignal != null) {
            selfCancelled = true;
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        if (!selfCancelled) {
            showError(errString);
            imageView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    callback.onError();
                }
            }, ERROR_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError(helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        showError(imageView.getResources().getString(R.string.fingerprint_not_recognized));
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        errorTextView.removeCallbacks(resetErrorTextRunnable);
        imageView.setImageResource(R.drawable.ic_fingerprint_success);
        errorTextView.setTextColor(
                errorTextView.getResources().getColor(R.color.success_color, null));
        errorTextView.setText(
                errorTextView.getResources().getString(R.string.fingerprint_success));
        imageView.postDelayed(new Runnable() {
            @Override
            public void run() {
                callback.onAuthenticated();
            }
        }, SUCCESS_DELAY_MILLIS);
    }

    private void showError(CharSequence error) {
        imageView.setImageResource(R.drawable.ic_fingerprint_error);
        errorTextView.setText(error);
        errorTextView.setTextColor(
                errorTextView.getResources().getColor(R.color.warning_color, null));
        errorTextView.removeCallbacks(resetErrorTextRunnable);
        errorTextView.postDelayed(resetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    Runnable resetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            errorTextView.setTextColor(
                    errorTextView.getResources().getColor(R.color.hint_color, null));
            errorTextView.setText(
                    errorTextView.getResources().getString(R.string.fingerprint_hint));
            imageView.setImageResource(R.drawable.ic_fp_40px);
        }
    };

    public interface Callback {

        void onAuthenticated();

        void onError();
    }
}
