package loader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import wtf.carnation.Client;

public final class CarnationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new Client();
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            if (Client.instance != null) Client.instance.shutdown();
        });
    }
}
