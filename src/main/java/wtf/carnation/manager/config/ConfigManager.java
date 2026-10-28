package wtf.carnation.manager.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.carnation.Client;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores each module's state, key and settings in Carnation/modules.json. */
public class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = Client.clientFolder.toPath().resolve("modules.json");

    public void loadAllConfig() {
        if (!Files.isRegularFile(file)) {
            saveAllConfig();
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                Client.logger.warn("{} is not a JSON object, recreating it", file);
                saveAllConfig();
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (Module module : Client.instance.getModuleManager().getModules()) {
                if (!root.has(module.getName()) || !root.get(module.getName()).isJsonObject()) continue;
                JsonObject object = root.getAsJsonObject(module.getName());
                if (object.has("key")) module.setKey(object.get("key").getAsInt());
                if (object.has("values") && object.get("values").isJsonObject()) {
                    JsonObject values = object.getAsJsonObject("values");
                    for (Value<?> value : module.getValues()) {
                        if (values.has(value.getName())) value.fromJson(values.get(value.getName()));
                    }
                }
                if (object.has("enabled") && !module.isMomentaryAction()) {
                    module.setState(object.get("enabled").getAsBoolean());
                }
            }
        } catch (IOException | RuntimeException e) {
            Client.logger.error("Failed to load {}", file, e);
        }
    }

    public void saveAllConfig() {
        JsonObject root = new JsonObject();
        for (Module module : Client.instance.getModuleManager().getModules()) {
            JsonObject object = new JsonObject();
            object.addProperty("enabled", module.isEnabled() && !module.isMomentaryAction());
            object.addProperty("key", module.getKey());
            JsonObject values = new JsonObject();
            for (Value<?> value : module.getValues()) {
                values.add(value.getName(), value.toJson());
            }
            object.add("values", values);
            root.add(module.getName(), object);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Client.logger.error("Failed to save {}", file, e);
        }
    }
}
