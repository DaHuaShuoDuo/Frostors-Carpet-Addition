package top.frostor.carpet.playerladder;

import carpet.api.settings.Rule;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.commands.CommandSourceStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class PlayerLadderCommandTest {
    private static final List<String> OPTIONS = List.of(
            "interactionMode",
            "clickMode",
            "pickupDepthLimit",
            "rideDepthLimit",
            "allowNonPlayers",
            "ignoredEntities",
            "dismountOnGameModeChange",
            "allowRiderStacking",
            "allowFakePlayers"
    );
    private static int checks;

    private PlayerLadderCommandTest() {
    }

    public static void main(String[] args) throws IOException {
        hasEveryCarpetRule();
        copiesCarpetChildBranches();
        hasCompleteTranslations("en_us");
        hasCompleteTranslations("zh_cn");

        System.out.println("PlayerLadderCommandTest passed " + checks + " checks");
    }

    private static void hasEveryCarpetRule() {
        for(String option : OPTIONS) {
            try {
                Field field = PlayerLadderSettings.class.getDeclaredField(option);
                check(field.getAnnotation(Rule.class) != null,
                        "Player Ladder option " + option + " must use @Rule");
            }catch(NoSuchFieldException exception) {
                check(false, "missing Player Ladder setting field " + option);
            }
        }
    }

    private static void copiesCarpetChildBranches() {
        Command<CommandSourceStack> listCommand = context -> 17;
        var value = RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                        "value",
                        StringArgumentType.word()
                )
                .suggests((context, builder) -> builder.suggest("carry_on").buildFuture())
                .executes(context -> 1);
        var rule = RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                        "rule",
                        StringArgumentType.word()
                )
                .then(value);
        LiteralCommandNode<CommandSourceStack> generated =
                LiteralArgumentBuilder.<CommandSourceStack>literal("playerladder")
                        .then(rule)
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list").executes(listCommand))
                        .build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(PlayerLadderCommand.copyRoot(generated, source -> true));

        var root = dispatcher.getRoot().getChild("playerladder");
        check(root != null, "the copied root must be registered");
        check(root.getChild("rule") != null, "the copied root must retain the rule branch");
        check(root.getChild("rule").getChild("value") != null,
                "the copied rule argument must retain its nested value branch");
        check(root.getChild("list") == null, "the copied root must not expose a separate list branch");
        check(root.getCommand() == listCommand, "the root command must reuse Carpet's list implementation");
    }

    private static void hasCompleteTranslations(String language) throws IOException {
        String path = "assets/frostors-carpet-addition/lang/" + language + ".json";
        try(InputStream stream = PlayerLadderCommandTest.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "missing language resource " + language);
            Map<String, String> translations = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() {}.getType()
            );
            check(translations != null, "failed to read language resource " + language);
            check(translations.containsKey("playerladder.category.playerladder"),
                    "missing Player Ladder category translation in " + language);

            for(String option : OPTIONS) {
                String prefix = "playerladder.rule." + option;
                check(translations.containsKey(prefix + ".name"), "missing option name " + option + " in " + language);
                check(translations.containsKey(prefix + ".desc"), "missing option description " + option + " in " + language);
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
