package np.com.grishma.fingerprintmanagerdemo.withdialogactivity.server;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * An entity that represents a single transaction (purchase) of an item.
 */
public class Transaction {

    /** The unique ID of the item of the purchase */
    private final Long mItemId;

    /** The unique user ID who made the transaction */
    private final String mUserId;

    /**
     * The random long value that will be also signed by the private key and verified in the server
     * that the same nonce can't be reused to prevent replay attacks.
     */
    private final Long mClientNonce;

    public Transaction(String userId, long itemId, long clientNonce) {
        mItemId = itemId;
        mUserId = userId;
        mClientNonce = clientNonce;
    }

    public String getUserId() {
        return mUserId;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = null;
        try {
            dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            dataOutputStream.writeLong(mItemId);
            dataOutputStream.writeUTF(mUserId);
            dataOutputStream.writeLong(mClientNonce);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
            } catch (IOException ignore) {
            }
            try {
                byteArrayOutputStream.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Transaction that = (Transaction) o;
        return Objects.equals(mItemId, that.mItemId) && Objects.equals(mUserId, that.mUserId) &&
                Objects.equals(mClientNonce, that.mClientNonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mItemId, mUserId, mClientNonce);
    }
}
