package cn.mythicland.dreamlib.command;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRouterTest {

    @Test
    void annotatedRootHandlerHandlesBareRootCommand() {
        AtomicBoolean executed = new AtomicBoolean();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "edit");
        router.registerAnnotated(new RootCommand(executed));

        assertTrue(router.onCommand(sender(), null, "edit", new String[0]));
        assertTrue(executed.get());
    }

    @Test
    void annotatedRootHandlerReceivesPositionalArguments() {
        AtomicReference<List<String>> received = new AtomicReference<>();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "gm");
        router.registerAnnotated(new RootArgumentCommand(received));

        assertTrue(router.onCommand(sender(), null, "gm", new String[]{"0"}));
        assertEquals(List.of("0"), received.get());
        assertEquals(
                List.of("0", "1", "2", "3"),
                router.onTabComplete(sender(), null, "gm", new String[]{""})
        );
    }

    @Test
    void annotatedPermissionAndCompleterAreApplied() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new AnnotatedCommand());

        assertEquals(
                List.of("one"),
                router.onTabComplete(sender(), null, "example", new String[]{"reload", ""})
        );
    }

    @Test
    void nestedAnnotatedPathsRouteArgumentsAndCompletion() {
        List<String> received = new ArrayList<>();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "nested");
        router.registerAnnotated(new NestedCommand(received));

        assertTrue(router.onCommand(
                sender(),
                null,
                "nested",
                new String[]{"cmd", "add", "player", "say", "hello"}
        ));
        assertEquals(List.of("player", "say", "hello"), received);
        assertEquals(
                List.of("player", "console"),
                router.onTabComplete(sender(), null, "nested", new String[]{"cmd", "add", ""})
        );
    }

    @Test
    void dynamicPermissionIsResolvedAtInvocationTime() {
        DynamicPermissionCommand command = new DynamicPermissionCommand();
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "dynamic");
        router.registerAnnotated(command);

        assertTrue(router.onCommand(
                sender(new ArrayList<>(), permission -> permission.equals("first")),
                null,
                "dynamic",
                new String[0]
        ));
        command.permission = "second";
        List<String> messages = new ArrayList<>();
        assertTrue(router.onCommand(
                sender(messages, permission -> permission.equals("first")),
                null,
                "dynamic",
                new String[0]
        ));
        assertTrue(messages.stream().anyMatch(message -> message.contains("没有执行此命令的权限")));
    }

    @Test
    void rootPrefixesAndHelpCategoriesCompleteForAliasesAndRespectPermissions() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new GroupedCommand());
        var admin = sender(new ArrayList<>(), permission -> true);
        var player = sender(new ArrayList<>(), permission -> false);
        assertTrue(router.onTabComplete(admin, null, "ex", new String[]{""}).containsAll(List.of("items", "help")));
        assertEquals(List.of("items"), router.onTabComplete(admin, null, "ex", new String[]{"it"}));
        assertEquals(List.of("help"), router.onTabComplete(admin, null, "ex", new String[]{"he"}));
        assertEquals(List.of("edit"), router.onTabComplete(admin, null, "ex", new String[]{"items", "e"}));
        assertEquals(List.of("alpha"), router.onTabComplete(admin, null, "ex", new String[]{"items", "edit", "al"}));
        assertEquals(List.of("items"), router.onTabComplete(admin, null, "ex", new String[]{"help", "i"}));
        assertEquals(List.of(), router.onTabComplete(player, null, "ex", new String[]{"help", "i"}));
        assertEquals(List.of(), router.onTabComplete(player, null, "ex", new String[]{"it"}));
    }

    @Test
    void groupedHelpUsesTheInvokedAliasAndSortedAuthorizedCommands() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new GroupedCommand());
        List<String> messages = new ArrayList<>();
        var admin = sender(messages, permission -> true);
        router.onCommand(admin, null, "ex", new String[]{"help"});
        assertEquals(3, messages.size());
        assertTrue(messages.get(1).contains("/ex help player"));
        assertTrue(messages.get(2).contains("/ex help items"));
        messages.clear();
        router.onCommand(admin, null, "ex", new String[]{"help", "items"});
        assertEquals(3, messages.size());
        assertTrue(messages.get(1).contains("/ex items edit"));
        assertTrue(messages.get(2).contains("/ex items reload"));
        messages.clear();
        router.onCommand(sender(messages, permission -> false), null, "ex", new String[]{"help"});
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("/ex help player"));
    }

    @Test
    void treeHelpShowsSortedRedCommandsAndNestedHelpForInvalidInput() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new TreeCommand());
        List<String> messages = new ArrayList<>();
        var admin = sender(messages, permission -> true);
        router.onCommand(admin, null, "ex", new String[]{"help"});
        assertEquals(List.of("§4> §c/ex combat", "§4> §c/ex items", "§4> §c/ex npc"), messages);
        messages.clear();
        router.onCommand(admin, null, "ex", new String[]{"items", "wrong"});
        assertEquals(List.of("§4> §c/ex items", "§4> §c/ex items edit <id>"), messages);
        messages.clear();
        router.onCommand(admin, null, "ex", new String[]{"combat", "wrong"});
        assertEquals(List.of("§4> §c/ex combat reload"), messages);
        messages.clear();
        router.onCommand(admin, null, "ex", new String[]{"unrecognized"});
        assertEquals(List.of("§4> §c/ex combat", "§4> §c/ex items", "§4> §c/ex npc"), messages);
        messages.clear();
        router.onCommand(sender(messages, permission -> false), null, "ex", new String[]{"help"});
        assertEquals(List.of("§4> §c/ex npc"), messages);
        assertEquals(List.of("combat", "items", "npc"), router.onTabComplete(admin, null, "ex", new String[]{"help", ""}));
        assertEquals(List.of("edit"), router.onTabComplete(admin, null, "ex", new String[]{"help", "items", ""}));
        assertEquals(List.of("legacy-id"), router.onTabComplete(admin, null, "ex", new String[]{"npc", "lega"}));
    }

    @Test
    void bareTreeCommandWithoutRootHandlerShowsHelp() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new TreeWithoutRoot());
        List<String> messages = new ArrayList<>();
        router.onCommand(sender(messages, permission -> true), null, "ex", new String[0]);
        assertEquals(List.of("§4> §c/ex items", "§4> §c/ex reload"), messages);
    }

    @Test
    void standardHelpAndInvalidUsageUseTheSameArrowAndAlias() {
        CommandRouter router = new CommandRouter(Logger.getLogger("CommandRouterTest"), "example");
        router.registerAnnotated(new AnnotatedCommand());
        List<String> messages = new ArrayList<>();
        var admin = sender(messages, permission -> true);
        router.onCommand(admin, null, "ex", new String[]{"help"});
        assertEquals(List.of("§4> §c/ex reload"), messages);
        messages.clear();
        router.onCommand(admin, null, "ex", new String[]{"reload"});
        assertEquals(List.of("§4> §c/ex reload"), messages);
    }

    @Test
    void commonMessageColorsAreAvailable() {
        assertTrue(VanillaCommandMessages.red("message").startsWith(ChatColor.RED.toString()));
        assertTrue(VanillaCommandMessages.yellow("message").startsWith(ChatColor.YELLOW.toString()));
        assertTrue(VanillaCommandMessages.green("message").startsWith(ChatColor.GREEN.toString()));
    }

    @Test
    void multilineUsagePrefixesEveryCommandLine() {
        String usage = VanillaCommandMessages.usage("/worldregion landmark set <id> <显示名>\n"
                + "/worldregion landmark delete <id>\n"
                + "/worldregion landmark list");

        assertEquals(
                ChatColor.DARK_RED + "> " + ChatColor.RED + "/worldregion landmark set <id> <显示名>\n"
                        + ChatColor.DARK_RED + "> " + ChatColor.RED + "/worldregion landmark delete <id>\n"
                        + ChatColor.DARK_RED + "> " + ChatColor.RED + "/worldregion landmark list",
                usage
        );
    }

    @Test
    void rootCauseMessageUsesTheDeepestCause() {
        Throwable error = new IllegalStateException(
                "outer",
                new IllegalArgumentException("inner")
        );

        assertEquals("inner", DreamLibApi.rootCauseMessage(error));
    }

    private CommandSender sender() {
        return sender(new ArrayList<>(), permission -> true);
    }

    private CommandSender sender(List<String> messages, Predicate<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.add((String) arguments[0]);
                        return null;
                    }
                    if (method.getName().equals("hasPermission")) {
                        return permissions.test((String) arguments[0]);
                    }
                    if (method.getReturnType() == boolean.class) return true;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0.0F;
                    if (method.getReturnType() == double.class) return 0.0D;
                    if (method.getReturnType() == short.class) return (short) 0;
                    if (method.getReturnType() == byte.class) return (byte) 0;
                    if (method.getReturnType() == char.class) return (char) 0;
                    return null;
                }
        );
    }

    @CommandComponent("edit")
        private record RootCommand(AtomicBoolean executed) {

        @CommandHandler
            private void execute(CommandContext context) {
                context.requireArguments(0);
                executed.set(true);
            }
        }

    @CommandComponent("gm")
        private record RootArgumentCommand(AtomicReference<List<String>> received) {

        @CommandHandler
            private void execute(CommandContext context) {
                received.set(context.arguments());
            }

            @CommandCompleter
            private List<String> complete(CommandContext context) {
                return List.of("0", "1", "2", "3");
            }
        }

    @CommandComponent("example")
    private static final class AnnotatedCommand {

        @CommandHandler(value = "reload", permission = "example.reload")
        private void reload(CommandContext context) {
            context.requireArguments(1);
        }

        @CommandCompleter("reload")
        private List<String> complete(CommandContext context) {
            return List.of("one");
        }
    }

    @CommandComponent("nested")
        private record NestedCommand(List<String> received) {

        @CommandHandler(value = "cmd add", permission = "nested.add")
            private void add(CommandContext context) {
                context.requireAtLeast(3);
                received.addAll(context.arguments());
            }

            @CommandCompleter("cmd add")
            private List<String> complete(CommandContext context) {
                return List.of("player", "console");
            }
        }

    @CommandComponent(value = "example", helpGroups = {"player:玩家", "items:物品"})
    private static final class GroupedCommand {
        @CommandHandler(helpGroup = "player")
        private void home(CommandContext context) { }

        @CommandHandler(value = "items reload", helpGroup = "items", permission = "example.admin")
        private void reload(CommandContext context) { }

        @CommandHandler(value = "items edit", helpGroup = "items", permission = "example.admin")
        private void edit(CommandContext context) { }

        @CommandCompleter("items edit")
        private List<String> completeEdit(CommandContext context) { return List.of("alpha", "beta"); }
    }

    @CommandComponent(value = "example", treeHelp = true)
    private static final class TreeCommand {
        @CommandHandler
        private void home(CommandContext context) { context.requireArguments(0); }

        @CommandHandler(value = "items", permission = "admin")
        private void items(CommandContext context) { context.requireArguments(0); }

        @CommandHandler(value = "items edit", permission = "admin", usage = "/example items edit <id>")
        private void edit(CommandContext context) { context.requireArguments(1); }

        @CommandHandler(value = "combat reload", permission = "admin")
        private void reload(CommandContext context) { context.requireArguments(0); }

        @CommandHandler(value = "npc", showInHelp = false)
        private void legacyNpc(CommandContext context) { context.requireArguments(1); }

        @CommandCompleter("npc")
        private List<String> npcComplete(CommandContext context) { return List.of("legacy-id"); }

        @CommandHandler(value = "npc list")
        private void npcList(CommandContext context) { context.requireArguments(0); }
    }

    @CommandComponent(value = "example", treeHelp = true)
    private static final class TreeWithoutRoot {
        @CommandHandler(value = "items")
        private void items(CommandContext context) { }

        @CommandHandler(value = "reload")
        private void reload(CommandContext context) { }
    }

    @CommandComponent("dynamic")
    private static final class DynamicPermissionCommand {

        private String permission = "first";

        @CommandHandler(permissionMethod = "permission")
        private void execute(CommandContext context) {
            context.requireArguments(0);
        }

        private String permission() {
            return permission;
        }
    }
}
