package np.com.grishma.fingerprintmanagerdemo.withdialogactivity.server;

import java.security.PublicKey;

/**
 * An interface that defines the methods required for the store backend.
 */
public interface StoreBackend {

    /**
     * Verifies the authenticity of the provided transaction by confirming that it was signed with
     * the private key enrolled for the userId.
     *
     * @param transaction          the contents of the purchase transaction, its contents are
     *                             signed
     *                             by the
     *                             private key in the client side.
     * @param transactionSignature the signature of the transaction's contents.
     * @return true if the signedSignature was verified, false otherwise. If this method returns
     * true, the server can consider the transaction is successful.
     */
    boolean verify(Transaction transaction, byte[] transactionSignature);

    /**
     * Verifies the authenticity of the provided transaction by password.
     *
     * @param transaction the contents of the purchase transaction, its contents are signed by the
     *                    private key in the client side.
     * @param password    the password for the user associated with the {@code transaction}.
     * @return true if the password is verified.
     */
    boolean verify(Transaction transaction, String password);

    /**
     * Enrolls a public key associated with the userId
     *
     * @param userId    the unique ID of the user within the app including server side
     *                  implementation
     * @param password  the password for the user for the server side
     * @param publicKey the public key object to verify the signature from the user
     * @return true if the enrollment was successful, false otherwise
     */
    boolean enroll(String userId, String password, PublicKey publicKey);
}
