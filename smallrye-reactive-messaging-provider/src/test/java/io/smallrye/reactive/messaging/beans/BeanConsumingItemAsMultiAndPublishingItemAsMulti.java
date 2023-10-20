package io.smallrye.reactive.messaging.beans;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.reactivex.Flowable;
import io.smallrye.mutiny.Multi;
import mutiny.zero.flow.adapters.AdaptersToFlow;

@ApplicationScoped
public class BeanConsumingItemAsMultiAndPublishingItemAsMulti {

    @Incoming("count")
    @Outgoing("sink")
    public Multi<String> process(Multi<Integer> source) {
        return source
                .map(i -> i + 1)
                .flatMap(i -> AdaptersToFlow.publisher(Flowable.just(i, i)))
                .map(i -> Integer.toString(i));
    }

}
