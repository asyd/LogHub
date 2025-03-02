package loghub;

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import loghub.decoders.Decoder.DecodeException;

public final class Stats {
    public final static AtomicLong received = new AtomicLong();
    public final static AtomicLong dropped = new AtomicLong();
    public final static AtomicLong sent = new AtomicLong();
    public final static AtomicLong failed = new AtomicLong();
    public final static AtomicLong thrown = new AtomicLong();
    public final static AtomicLong blocked = new AtomicLong();
    public final static AtomicLong failedSend = new AtomicLong();
    public final static AtomicLong failedReceived = new AtomicLong();

    private final static Queue<ProcessingException> errors = new ArrayBlockingQueue<>(100);
    private final static Queue<DecodeException> decodeErrors = new ArrayBlockingQueue<>(100);
    private final static Queue<Throwable> exceptions = new ArrayBlockingQueue<>(100);
    private final static Queue<String> blockedMessage = new ArrayBlockingQueue<>(100);
    private final static Queue<String> senderErrors = new ArrayBlockingQueue<>(100);
    private final static Queue<String> receiverErrors = new ArrayBlockingQueue<>(100);

    private Stats() {
    }

    public static synchronized void reset() {
        errors.clear();
        exceptions.clear();
        received.set(0);
        dropped.set(0);
        sent.set(0);
        failed.set(0);
        thrown.set(0);
        failedSend.set(0);
        decodeErrors.clear();
        blockedMessage.clear();
        senderErrors.clear();
    }

    public static synchronized void newDecodError(DecodeException e) {
        failed.incrementAndGet();
        try {
            decodeErrors.add(e);
        } catch (IllegalStateException ex) {
            decodeErrors.remove();
            decodeErrors.add(e);
        }
    }

    public static synchronized void newError(ProcessingException e) {
        failed.incrementAndGet();
        try {
            errors.add(e);
        } catch (IllegalStateException ex) {
            errors.remove();
            errors.add(e);
        }
    }

    public static synchronized void newException(Throwable e) {
        thrown.incrementAndGet();
        try {
            exceptions.add(e);
        } catch (IllegalStateException ex) {
            exceptions.remove();
            exceptions.add(e);
        }
    }

    public static synchronized void newBlockedError(String context) {
        blocked.incrementAndGet();
        try {
            blockedMessage.add(context);
        } catch (IllegalStateException ex) {
            blockedMessage.remove();
            blockedMessage.add(context);
        }
    }

    public static synchronized void newSenderError(String context) {
        failedSend.incrementAndGet();
        try {
            senderErrors.add(context);
        } catch (IllegalStateException ex) {
            senderErrors.remove();
            senderErrors.add(context);
        }
    }

    public static synchronized void newReceivedError(String context) {
        failedReceived.incrementAndGet();
        try {
            receiverErrors.add(context);
        } catch (IllegalStateException ex) {
            receiverErrors.remove();
            receiverErrors.add(context);
        }
    }

    public static synchronized Collection<ProcessingException> getErrors() {
        return Collections.unmodifiableCollection(errors);
    }

    public static synchronized Collection<DecodeException> getDecodeErrors() {
        return Collections.unmodifiableCollection(decodeErrors);
    }

    public static Collection<Throwable> getExceptions() {
        return Collections.unmodifiableCollection(exceptions);
    }

    public static Collection<String> getBlockedError() {
        return Collections.unmodifiableCollection(blockedMessage);
    }

    public static Collection<String> getSenderError() {
        return Collections.unmodifiableCollection(senderErrors);
    }

    public static Collection<String> getReceiverError() {
        return Collections.unmodifiableCollection(receiverErrors);
    }

}
