package me.tg.worldrepos.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.BufferedWriter;

public class WorldreposClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen && !(screen instanceof TitleScreenWithButton)) {
                client.setScreen(new TitleScreenWithButton());
            }
        });
    }

    // Subclass TitleScreen and add the button inside init()
    private static class TitleScreenWithButton extends TitleScreen {
        private final MinecraftClient client = MinecraftClient.getInstance();

        @Override
        protected void init() {
            super.init();
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("World Repos"),
                    button -> client.setScreen(new WorldRepoManagerScreen(this))
            ).dimensions(10, 10, 120, 20).build());
        }
    }

    // ----- WorldRepoManagerScreen -----
    public static class WorldRepoManagerScreen extends Screen {
        private final Screen parent;



        public WorldRepoManagerScreen(Screen parent) {
            super(Text.of("World Repo Manager"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            addDrawableChild(ButtonWidget.builder(Text.of("New Repo"), button -> {
                MinecraftClient.getInstance().setScreen(new NewRepoInputScreen(this));
            }).dimensions(width / 2 - 100, height / 4, 200, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.of("Browse Public Repos"), button -> {
                MinecraftClient.getInstance().setScreen(new PublicRepoScreen(this));
            }).dimensions(width / 2 - 100, height / 4 + 30, 200, 20).build());

            List<String> repos = WorldReposStorage.loadRepos();
            int y = height / 4 + 60;
            for (String repo : repos) {
                addDrawableChild(ButtonWidget.builder(Text.of(repo), b -> {
                    MinecraftClient.getInstance().setScreen(new RepoDetailsScreen(this, repo));
                }).dimensions(width / 2 - 100, y, 200, 20).build());

                addDrawableChild(ButtonWidget.builder(Text.of("X"), b -> {
                    WorldReposStorage.removeRepo(repo);
                    MinecraftClient.getInstance().setScreen(new WorldRepoManagerScreen(parent));
                }).dimensions(width / 2 + 110, y, 20, 20).build());
                y += 25;
            }
        }

        private static final Identifier BACKGROUND = new Identifier("worldrepos", "textures/gui/custom_background.png");
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the custom background texture
            context.drawTexture(BACKGROUND, 0, 0, 0, 0, this.width, this.height, this.width, this.height);

            // Then draw everything else
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ----- NewRepoInputScreen -----
    public static class NewRepoInputScreen extends Screen {
        private final Screen parent;
        private TextFieldWidget input;

        public NewRepoInputScreen(Screen parent) {
            super(Text.of("Add New Repo"));
            this.parent = parent;
        }
        public static final Logger LOGGER = LoggerFactory.getLogger("World-Repos");
        @Override
        protected void init() {
            input = new TextFieldWidget(textRenderer, width / 2 - 100, height / 3, 200, 20, Text.of("Repo URL"));
            input.setMaxLength(1024); // or any number you want
            addSelectableChild(input);
            addDrawableChild(input);

            addDrawableChild(ButtonWidget.builder(Text.of("Add Repo"), button -> {

                String url = input.getText();
                LOGGER.info("Url gotten: "+url);
                if (!url.isEmpty()) {
                    WorldReposStorage.addRepo(url);
                    MinecraftClient.getInstance().setScreen(new RepoDetailsScreen(this, url));
                }
            }).dimensions(width / 2 - 100, height / 3 + 30, 200, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), b -> {
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(width / 2 - 100, height / 3 + 60, 200, 20).build());
        }

        private static final Identifier BACKGROUND = new Identifier("worldrepos", "textures/gui/custom_background.png");
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the custom background texture
            context.drawTexture(BACKGROUND, 0, 0, 0, 0, this.width, this.height, this.width, this.height);

            // Then draw everything else
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ----- PublicRepoScreen -----
    public static class PublicRepoScreen extends Screen {
        private final Screen parent;

        public PublicRepoScreen(Screen parent) {
            super(Text.of("Public Repos"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            new Thread(() -> {
                try {
                    URL url = new URL("https://tg.is-a.dev/world-repos/public-repos.json");
                    JsonElement root = JsonParser.parseReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
                    JsonObject json = root.getAsJsonObject();
                    MinecraftClient.getInstance().execute(() -> {
                        int y = height / 4;
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            String name = entry.getKey();
                            String repoUrl = entry.getValue().getAsString();
                            addDrawableChild(ButtonWidget.builder(Text.of(name), b -> {
                                MinecraftClient.getInstance().setScreen(new RepoDetailsScreen(this, repoUrl));
                            }).dimensions(width / 2 - 100, y, 200, 20).build());
                            y += 25;
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            addDrawableChild(ButtonWidget.builder(Text.of("Back"), b -> MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(width / 2 - 100, height - 40, 200, 20).build());
        }

        private static final Identifier BACKGROUND = new Identifier("worldrepos", "textures/gui/custom_background.png");
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the custom background texture
            context.drawTexture(BACKGROUND, 0, 0, 0, 0, this.width, this.height, this.width, this.height);

            // Then draw everything else
            super.render(context, mouseX, mouseY, delta);
        }



        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ----- RepoDetailsScreen -----
    public static class RepoDetailsScreen extends Screen {
        private final Screen parent;
        private final String repoUrl;

        public RepoDetailsScreen(Screen parent, String repoUrl) {
            super(Text.of("Repo Details"));
            this.parent = parent;
            this.repoUrl = repoUrl;
        }

        @Override
        protected void init() {
            new Thread(() -> {
                try {
                    URL url = new URL(repoUrl.endsWith(".json") ? repoUrl : repoUrl + "/MinecraftWorldRepo.json");
                    JsonElement root = JsonParser.parseReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
                    JsonObject json = root.getAsJsonObject();

                    JsonObject info = json.getAsJsonObject("info");
                    String name = info.get("name").getAsString();
                    String desc = info.get("description").getAsString();
                    String author = info.get("author").getAsString();

                    MinecraftClient.getInstance().execute(() -> {
                        addDrawableChild(ButtonWidget.builder(Text.of(name + " by " + author), b -> {})
                                .dimensions(width / 2 - 100, height / 4, 200, 20).build());

                        JsonElement worldsElem = json.get("worlds");
                        if (worldsElem != null && worldsElem.isJsonArray()) {
                            int y = height / 4 + 30;
                            for (JsonElement wElem : worldsElem.getAsJsonArray()) {
                                JsonObject world = wElem.getAsJsonObject();
                                String worldName = world.get("name").getAsString();
                                String worldDesc = world.get("description").getAsString();
                                String zip = world.get("location").getAsString();
                                addDrawableChild(ButtonWidget.builder(Text.of(worldName + " - " + worldDesc), b -> {
                                    MinecraftClient.getInstance().setScreen(new WorldDownloadConfirmScreen(this, repoUrl + "/" + zip, worldName));
                                }).dimensions(width / 2 - 100, y, 200, 20).build());
                                y += 25;
                            }
                        }

                        addDrawableChild(ButtonWidget.builder(Text.of("Back"), b -> MinecraftClient.getInstance().setScreen(parent))
                                .dimensions(width / 2 - 100, height - 40, 200, 20).build());
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        private static final Identifier BACKGROUND = new Identifier("worldrepos", "textures/gui/custom_background.png");
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the custom background texture
            context.drawTexture(BACKGROUND, 0, 0, 0, 0, this.width, this.height, this.width, this.height);

            // Then draw everything else
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ----- WorldDownloadConfirmScreen -----
    public static class WorldDownloadConfirmScreen extends Screen {
        private final Screen parent;
        private final String zipUrl;
        private final String worldName;

        public WorldDownloadConfirmScreen(Screen parent, String zipUrl, String worldName) {
            super(Text.of("Download World?"));
            this.parent = parent;
            this.zipUrl = zipUrl;
            this.worldName = worldName;
        }

        @Override
        protected void init() {
            addDrawableChild(ButtonWidget.builder(Text.of("Download & Save"), b -> {
                new Thread(() -> {
                    try {
                        URL url = new URL(zipUrl);
                        try (ZipInputStream zipIn = new ZipInputStream(url.openStream())) {
                            ZipEntry entry;
                            Path savePath = Path.of(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), "saves", worldName);
                            Files.createDirectories(savePath);
                            while ((entry = zipIn.getNextEntry()) != null) {
                                Path filePath = savePath.resolve(entry.getName());
                                if (entry.isDirectory()) {
                                    Files.createDirectories(filePath);
                                } else {
                                    Files.createDirectories(filePath.getParent());
                                    Files.copy(zipIn, filePath);
                                }
                                zipIn.closeEntry();
                            }
                        }

                        MinecraftClient.getInstance().execute(() -> {
                            this.client.getToastManager().add(
                                    SystemToast.create(
                                            this.client,
                                            SystemToast.Type.NARRATOR_TOGGLE,
                                            Text.of("Download Complete"),
                                            Text.of("World \"" + worldName + "\" saved!")
                                    )
                            );
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(width / 2 - 100, height / 2, 200, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), b -> MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(width / 2 - 100, height / 2 + 30, 200, 20).build());
        }

        private static final Identifier BACKGROUND = new Identifier("worldrepos", "textures/gui/custom_background.png");
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the custom background texture
            context.drawTexture(BACKGROUND, 0, 0, 0, 0, this.width, this.height, this.width, this.height);

            // Then draw everything else
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ----- WorldReposStorage -----
    public static class WorldReposStorage {
        private static final Path STORAGE_PATH = Path.of("config/worldrepos/repos.txt");

        public static List<String> loadRepos() {
            try {
                if (Files.exists(STORAGE_PATH)) {
                    return Files.readAllLines(STORAGE_PATH);
                }
            } catch (IOException ignored) {}
            return new ArrayList<>();
        }

        public static void addRepo(String url) {
            List<String> repos = loadRepos();
            if (!repos.contains(url)) {
                repos.add(url);
                saveRepos(repos);
            }
        }

        public static void removeRepo(String url) {
            List<String> repos = loadRepos();
            repos.remove(url);
            saveRepos(repos);
        }

        private static void saveRepos(List<String> repos) {
            try {
                Files.createDirectories(STORAGE_PATH.getParent());
                BufferedWriter writer = Files.newBufferedWriter(STORAGE_PATH, StandardCharsets.UTF_8);
                for (String repo : repos) {
                    writer.write(repo + "\n");
                }
                writer.close();
            } catch (IOException ignored) {}
        }
    }
}
