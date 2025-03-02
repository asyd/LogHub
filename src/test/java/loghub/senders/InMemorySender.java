package loghub.senders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import loghub.BuilderClass;
import loghub.Event;

@BuilderClass(InMemorySender.Builder.class)
public class InMemorySender extends Sender {

    public static class Builder extends Sender.Builder<InMemorySender> {

        @Override
        public InMemorySender build() {
            return new InMemorySender(this);
        }
    }

    public InMemorySender(Builder builder) {
        super(builder);
    }

    private final List<Event> received = new ArrayList<>();

    @Override
    public boolean send(Event e) {
        received.add(e);
        return true;
    }

    @Override
    public String getSenderName() {
        return null;
    }

    public List<Event> getSendedEvents() {
        return Collections.unmodifiableList(received);
    }

}
