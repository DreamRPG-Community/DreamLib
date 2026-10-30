package cn.mythicland.dreamlib.command;

import cn.mythicland.dreamlib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared executor and tab completer for simple Bukkit subcommand trees.
 *
 * <p>All command execution is expected to happen on Bukkit's primary thread. The router owns
 * permission checks, usage handling, error logging, and command help formatting.</p>
 */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    private final Logger logger;
    private final String rootCommand;
    private final RouteNode commandRoot = new RouteNode();
    private final List<CommandNode> registeredCommands = new ArrayList<>();
    private CommandNode defaultCommand;
    private final Map<String, String> helpGroups = new LinkedHashMap<>();
    private boolean treeHelp;

    /**
     * Creates a router for one root command.
     *
     * @param owner       the plugin whose logger handles unexpected command failures
     * @param rootCommand the root command used in usage messages
     * @throws NullPointerException if an argument is null
     */
    public CommandRouter(JavaPlugin owner, String rootCommand) {
        this(Objects.requireNonNull(owner, "owner").getLogger(), rootCommand);
    }

    CommandRouter(Logger logger, String rootCommand) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.rootCommand = Objects.requireNonNull(rootCommand, "rootCommand");
    }

    private static String normalizeAnnotatedPath(String value) {
        String normalized = Objects.requireNonNull(value, "command path").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            if (part.equals("help")) {
                throw new IllegalArgumentException("The help command is reserved");
            }
        }
        return String.join(" ", parts);
    }

    private static List<Method> annotatedMethods(
            Class<?> componentType,
            Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = componentType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotationType)) methods.add(method);
            }
        }
        methods.sort(Comparator
                .comparingInt((Method method) -> methodOrder(method, annotationType))
                .thenComparing(Method::getName)
                .thenComparing(CommandRouter::methodSignature));
        return methods;
    }

    private static int methodOrder(
            Method method,
            Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        if (annotationType == CommandHandler.class) return method.getAnnotation(CommandHandler.class).order();
        return 0;
    }

    private static String methodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static void validateHandler(Method method) {
        if (Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class
                || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommandContext.class})) {
            throw new IllegalStateException(
                    "@CommandHandler must be an instance void method(CommandContext): "
                            + method
            );
        }
        if (!method.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @CommandHandler method: " + method);
        }
    }

    private static void validateCompleter(Method method) {
        if (Modifier.isStatic(method.getModifiers())
                || !List.class.isAssignableFrom(method.getReturnType())
                || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommandContext.class})) {
            throw new IllegalStateException(
                    "@CommandCompleter must be an instance List<String> method(CommandContext): "
                            + method
            );
        }
        if (!method.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @CommandCompleter method: " + method);
        }
    }

    private static PermissionBinding resolvePermission(
            Object component,
            CommandHandler annotation,
            String defaultPermission
    ) {
        String permission = normalizePermission(annotation.permission());
        if (!annotation.permissionMethod().isBlank()) {
            if (!permission.isBlank()) {
                throw new IllegalStateException(
                        "@CommandHandler cannot declare both permission and permissionMethod"
                );
            }
            Method method = findMethod(component.getClass(), annotation.permissionMethod().trim());
            if (method == null
                    || Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != String.class
                    || !method.trySetAccessible()) {
                throw new IllegalStateException(
                        "permissionMethod must be an instance String method with no arguments: "
                                + annotation.permissionMethod()
                );
            }
            return new PermissionBinding("", method);
        }
        if (permission.isBlank() && annotation.inheritPermission()) {
            return new PermissionBinding(defaultPermission, null);
        }
        return new PermissionBinding(permission, null);
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) return method;
            }
        }
        return null;
    }

    private static String normalizePermission(String value) {
        return Objects.requireNonNull(value, "permission").trim();
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException exception) return exception;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException("Annotated command method failed", throwable);
    }

    /**
     * Registers all {@link CommandHandler} methods on one command component.
     *
     * <p>Handlers use {@link CommandContext} and are adapted to Bukkit's executor and tab
     * completer interfaces by this router. The component's {@link CommandComponent#permission()}
     * is inherited by handlers unless a handler overrides it.</p>
     *
     * @param component annotated command component
     * @throws IllegalStateException if the component contains an invalid or incomplete binding
     */
    public void registerAnnotated(Object component) {
        Objects.requireNonNull(component, "component");
        CommandComponent componentAnnotation = component.getClass().getAnnotation(CommandComponent.class);
        if (componentAnnotation == null) {
            throw new IllegalStateException(
                    "Annotated command component is missing @CommandComponent: "
                            + component.getClass().getName()
            );
        }

        treeHelp = componentAnnotation.treeHelp();
        if (treeHelp && componentAnnotation.helpGroups().length > 0) {
            throw new IllegalStateException("Tree help and grouped help cannot both be enabled");
        }
        for (String spec : componentAnnotation.helpGroups()) {
            String[] group = spec.split(":", 2);
            if (group.length != 2 || !group[0].matches("[a-z][a-z0-9_-]*") || group[1].isBlank()
                    || helpGroups.putIfAbsent(group[0], group[1].trim()) != null) {
                throw new IllegalStateException("Invalid or duplicate help group: " + spec);
            }
        }
        Map<String, Method> completers = new LinkedHashMap<>();
        for (Method method : annotatedMethods(component.getClass(), CommandCompleter.class)) {
            CommandCompleter annotation = method.getAnnotation(CommandCompleter.class);
            validateCompleter(method);
            String name = normalizeAnnotatedPath(annotation.value());
            if (completers.putIfAbsent(name, method) != null) {
                throw new IllegalStateException("Duplicate command completer: " + name);
            }
        }

        List<Method> handlers = annotatedMethods(component.getClass(), CommandHandler.class);
        if (handlers.isEmpty()) {
            throw new IllegalStateException(
                    "Command component declares no @CommandHandler methods: "
                            + component.getClass().getName()
            );
        }

        String defaultPermission = normalizePermission(componentAnnotation.permission());
        Set<String> handledNames = new HashSet<>();
        for (Method method : handlers) {
            CommandHandler annotation = method.getAnnotation(CommandHandler.class);
            validateHandler(method);
            String name = normalizeAnnotatedPath(annotation.value());
            if (!helpGroups.isEmpty() && !helpGroups.containsKey(annotation.helpGroup())) {
                throw new IllegalStateException("Unknown help group for command: " + name);
            }
            if (!handledNames.add(name)) {
                throw new IllegalStateException("Duplicate annotated command handler: " + name);
            }
            Method completer = completers.remove(name);
            AnnotatedCommandNode subcommand = new AnnotatedCommandNode(
                    component,
                    method,
                    completer,
                    annotation,
                    name,
                    resolveUsage(annotation.usage(), name),
                    resolvePermission(component, annotation, defaultPermission),
                    rootCommand
            );
            if (name.isBlank()) registerDefault(subcommand, annotation);
            else register(subcommand);
        }
        if (!completers.isEmpty()) {
            throw new IllegalStateException(
                    "Command completer has no matching handler: " + completers.keySet()
            );
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            if (defaultCommand == null) {
                if (treeHelp) sendTreeHelp(sender, label, List.of());
                else sendShortHelp(sender);
            } else {
                executeCommand(sender, defaultCommand, List.of(), label, arguments);
            }
            return true;
        }

        if (arguments[0].equalsIgnoreCase("help")) {
            if (treeHelp) sendTreeHelp(sender, label, Arrays.asList(arguments).subList(1, arguments.length));
            else if (helpGroups.isEmpty()) sendHelp(sender, label);
            else sendGroupedHelp(sender, label, arguments);
            return true;
        }

        RouteMatch match = findRoute(arguments);
        if (match == null) {
            if (treeHelp) {
                sendTreeHelp(sender, label, closestTopic(arguments));
                return true;
            }
            if (defaultCommand != null && unknownRootPath(arguments[0])) {
                executeCommand(sender, defaultCommand, List.copyOf(Arrays.asList(arguments)), label, arguments);
                return true;
            }
            sender.sendMessage(VanillaCommandMessages.red("未知子命令: " + arguments[0]));
            sendShortHelp(sender);
            return true;
        }

        List<String> subcommandArguments = Arrays.asList(arguments).subList(match.consumedTokens(), arguments.length);
        executeCommand(sender, match.command(), List.copyOf(subcommandArguments), label, arguments);
        return true;
    }

    private void executeCommand(
            CommandSender sender,
            CommandNode subcommand,
            List<String> arguments,
            String label,
            String[] rawArguments
    ) {
        if (!subcommand.permission().isBlank() && !sender.hasPermission(subcommand.permission())) {
            sender.sendMessage(VanillaCommandMessages.red("你没有执行此命令的权限。"));
            return;
        }

        try {
            subcommand.execute(sender, arguments, label);
        } catch (CommandUsageException exception) {
            if (treeHelp) sendTreeHelp(sender, label, parentTopic(subcommand));
            else sender.sendMessage(VanillaCommandMessages.usage(usageForLabel(exception.usage(), label)));
        } catch (Exception exception) {
            logger.log(
                    Level.SEVERE,
                    "Command execution failed for /" + label + " " + String.join(" ", rawArguments),
                    exception
            );
            sender.sendMessage(VanillaCommandMessages.red("命令执行失败, 请查看服务端日志。"));
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length == 0) return List.of();
        if (arguments[0].equalsIgnoreCase("help") && treeHelp) {
            return completeTreeHelp(sender, arguments);
        }
        if (arguments[0].equalsIgnoreCase("help") && !helpGroups.isEmpty()) {
            if (arguments.length != 2) return List.of();
            String prefix = arguments[1].toLowerCase(Locale.ROOT);
            return helpGroups.keySet().stream().filter(key -> key.startsWith(prefix))
                    .filter(key -> !commandsForGroup(sender, key).isEmpty()).toList();
        }
        String first = arguments[0].toLowerCase(Locale.ROOT);
        boolean rootPrefix = (!commandRoot.children.isEmpty() && "help".startsWith(first))
                || commandRoot.children.keySet().stream().anyMatch(name -> name.startsWith(first));
        if (!arguments[0].equalsIgnoreCase("help")
                && defaultCommand != null
                && unknownRootPath(arguments[0]) && !rootPrefix) {
            if (!hasPermission(sender, defaultCommand)) return List.of();
            return completeCommand(sender, defaultCommand, List.copyOf(Arrays.asList(arguments)), alias);
        }

        RouteNode node = commandRoot;
        int consumedTokens = 0;
        while (consumedTokens < arguments.length) {
            String token = arguments[consumedTokens].toLowerCase(Locale.ROOT);
            RouteNode child = node.children.get(token);
            if (child == null) {
                RouteNode currentNode = node;
                Set<String> names = currentNode.children.keySet().stream()
                        .filter(name -> name.startsWith(token))
                        .filter(name -> hasAccessibleDescendant(sender, currentNode.children.get(name)))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (treeHelp && names.isEmpty() && node.command != null && hasPermission(sender, node.command)
                        && consumedTokens == arguments.length - 1 && !token.isEmpty()) {
                    return completeCommand(sender, node.command,
                            List.copyOf(Arrays.asList(arguments).subList(consumedTokens, arguments.length)), alias);
                }
                if (!node.children.isEmpty() && (!names.isEmpty() || consumedTokens == arguments.length - 1)) {
                    if (consumedTokens == 0 && "help".startsWith(token)) names.add("help");
                    return List.copyOf(names);
                }
                break;
            }
            node = child;
            consumedTokens++;
        }

        if (consumedTokens < arguments.length && node.command != null && hasPermission(sender, node.command)) {
            List<String> subcommandArguments = Arrays.asList(arguments).subList(consumedTokens, arguments.length);
            return completeCommand(sender, node.command, List.copyOf(subcommandArguments), alias);
        }

        if (consumedTokens == arguments.length && !node.children.isEmpty()) {
            return node.children.entrySet().stream()
                    .filter(entry -> hasAccessibleDescendant(sender, entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        if (node.command != null && hasPermission(sender, node.command)) {
            return completeCommand(sender, node.command, List.of(), alias);
        }
        return List.of();
    }

    private List<String> completeCommand(
            CommandSender sender,
            CommandNode subcommand,
            List<String> arguments,
            String label
    ) {
        try {
            List<String> completions = subcommand.tabComplete(sender, arguments, label);
            if (completions == null) return List.of();
            String prefix = arguments.isEmpty() ? "" : arguments.getLast().toLowerCase(Locale.ROOT);
            return completions.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Command tab completion failed for /" + rootCommand, exception);
            return List.of();
        }
    }

    private void sendShortHelp(CommandSender sender) {
        sender.sendMessage(VanillaCommandMessages.usage("/" + rootCommand + " <子命令>"));
        sender.sendMessage(VanillaCommandMessages.red(
                "使用 /" + rootCommand + " help 查看所有命令。"
        ));
    }

    private void sendHelp(CommandSender sender, String label) {
        for (CommandNode subcommand : uniqueCommands()) {
            if (hasPermission(sender, subcommand)) {
                sender.sendMessage(VanillaCommandMessages.usage(usageForLabel(subcommand.usage(), label)));
            }
        }
    }

    private List<String> closestTopic(String[] arguments) {
        RouteNode node = commandRoot;
        List<String> matched = new ArrayList<>();
        for (String argument : arguments) {
            RouteNode child = node.children.get(argument.toLowerCase(Locale.ROOT));
            if (child == null) break;
            matched.add(argument.toLowerCase(Locale.ROOT));
            node = child;
        }
        return matched;
    }

    private List<String> parentTopic(CommandNode command) {
        if (command.name().isBlank()) return List.of();
        List<String> parts = new ArrayList<>(List.of(command.name().split(" ")));
        if (parts.size() > 1) parts.removeLast();
        return parts;
    }

    private List<String> completeTreeHelp(CommandSender sender, String[] arguments) {
        if (arguments.length < 2) return List.of();
        String prefix =
                String.join(" ", Arrays.asList(arguments).subList(1, arguments.length - 1)).toLowerCase(Locale.ROOT);
        String current = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        String start = prefix.isEmpty() ? "" : prefix + " ";
        return visibleTreeCommands(sender).stream().map(CommandNode::name)
                .filter(name -> name.startsWith(start))
                .map(name -> name.substring(start.length()).split(" ")[0])
                .filter(name -> name.startsWith(current)).distinct().sorted().toList();
    }

    private void sendTreeHelp(CommandSender sender, String label, List<String> topic) {
        String path = String.join(" ", topic).toLowerCase(Locale.ROOT);
        List<CommandNode> visible = visibleTreeCommands(sender);
        boolean found = visible.stream().anyMatch(command -> command.name().equals(path)
                || command.name().startsWith(path + " "));
        String selected = found ? path : "";
        List<String> lines;
        if (selected.isEmpty()) {
            lines = visible.stream().map(command -> "/" + label + " " + command.name().split(" ")[0])
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } else {
            lines = visible.stream().filter(command -> command.name().equals(selected)
                            || command.name().startsWith(selected + " "))
                    .map(command -> usageForLabel(command.usage(), label))
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        for (String line : lines) sender.sendMessage(VanillaCommandMessages.usage(line));
    }

    private List<CommandNode> visibleTreeCommands(CommandSender sender) {
        return registeredCommands.stream().filter(CommandNode::showInHelp)
                .filter(command -> hasPermission(sender, command)).toList();
    }

    private String usageForLabel(String usage, String label) {
        String root = "/" + rootCommand;
        if (usage.equals(root) || usage.startsWith(root + " ")) return "/" + label + usage.substring(root.length());
        return usage;
    }

    private void sendGroupedHelp(CommandSender sender, String label, String[] arguments) {
        if (arguments.length == 1) {
            sender.sendMessage(VanillaCommandMessages.green("/" + label + " 帮助分类:"));
            helpGroups.forEach((key, description) -> {
                if (!commandsForGroup(sender, key).isEmpty()) {
                    sender.sendMessage(
                            VanillaCommandMessages.usage("/" + label + " help " + key + " - " + description));
                }
            });
            return;
        }
        if (arguments.length != 2) {
            sender.sendMessage(VanillaCommandMessages.usage("/" + label + " help [分类]"));
            return;
        }
        String key = arguments[1].toLowerCase(Locale.ROOT);
        List<CommandNode> commands = commandsForGroup(sender, key);
        if (!helpGroups.containsKey(key) || commands.isEmpty()) {
            sender.sendMessage(VanillaCommandMessages.red("未知或不可用的帮助分类: " + arguments[1]));
            return;
        }
        sender.sendMessage(VanillaCommandMessages.green(helpGroups.get(key) + " - 命令帮助:"));
        for (CommandNode command : commands) {
            String usage = command.usage();
            String root = "/" + rootCommand;
            if (usage.equals(root) || usage.startsWith(root + " "))
                usage = "/" + label + usage.substring(root.length());
            String description = command.description();
            sender.sendMessage(
                    VanillaCommandMessages.usage(usage + (description.isBlank() ? "" : " - " + description)));
        }
    }

    private List<CommandNode> commandsForGroup(CommandSender sender, String group) {
        return uniqueCommands().stream().filter(command -> command.helpGroup().equals(group))
                .filter(command -> hasPermission(sender, command))
                .sorted(Comparator.comparing(CommandNode::name)).toList();
    }

    private Collection<CommandNode> uniqueCommands() {
        Set<CommandNode> uniqueCommands = new LinkedHashSet<>();
        if (defaultCommand != null) uniqueCommands.add(defaultCommand);
        uniqueCommands.addAll(registeredCommands);
        return uniqueCommands;
    }

    private boolean hasPermission(CommandSender sender, CommandNode subcommand) {
        return subcommand.permission().isBlank() || sender.hasPermission(subcommand.permission());
    }

    private void register(CommandNode command) {
        Objects.requireNonNull(command, "command");
        registerPath(command.name(), command);
        for (String alias : command.aliases()) {
            String normalizedAlias = normalizeAnnotatedPath(alias);
            if (normalizedAlias.isBlank()) {
                throw new IllegalArgumentException("Command alias cannot be blank");
            }
            String[] commandParts = command.name().split(" ");
            String aliasPath = normalizedAlias.indexOf(' ') >= 0 || commandParts.length == 1
                    ? normalizedAlias
                    : command.name().substring(0, command.name().lastIndexOf(' ') + 1) + normalizedAlias;
            registerPath(aliasPath, command);
        }
        registeredCommands.add(command);
    }

    private void registerDefault(CommandNode command, CommandHandler annotation) {
        Objects.requireNonNull(command, "command");
        if (annotation.aliases().length > 0) {
            throw new IllegalStateException("The root command cannot declare aliases");
        }
        if (defaultCommand != null) {
            throw new IllegalStateException("A default command is already registered");
        }
        defaultCommand = command;
    }

    private void registerPath(String name, CommandNode command) {
        String normalizedName = normalizeAnnotatedPath(name);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Command path cannot be blank");
        }
        String[] parts = normalizedName.split(" ");
        RouteNode node = commandRoot;
        for (String part : parts) {
            node = node.children.computeIfAbsent(part, ignored -> new RouteNode());
        }
        if (node.command != null && node.command != command) {
            throw new IllegalArgumentException("Duplicate command path: " + name);
        }
        node.command = command;
    }

    private RouteMatch findRoute(String[] arguments) {
        RouteNode node = commandRoot;
        CommandNode command = null;
        int consumedTokens = 0;
        for (String argument : arguments) {
            RouteNode child = node.children.get(argument.toLowerCase(Locale.ROOT));
            if (child == null) break;
            node = child;
            consumedTokens++;
            if (node.command != null) command = node.command;
        }
        return command == null ? null : new RouteMatch(command, consumedTokens);
    }

    private boolean hasAccessibleDescendant(CommandSender sender, RouteNode node) {
        if (node.command != null && hasPermission(sender, node.command)) return true;
        return node.children.values().stream().anyMatch(child -> hasAccessibleDescendant(sender, child));
    }

    private boolean unknownRootPath(String argument) {
        return !commandRoot.children.containsKey(argument.toLowerCase(Locale.ROOT));
    }

    private String resolveUsage(String usage, String name) {
        String declared = Objects.requireNonNull(usage, "usage").trim();
        if (!declared.isBlank()) return declared;
        return name.isBlank() ? "/" + rootCommand : "/" + rootCommand + " " + name;
    }

    private interface CommandNode {

        String name();

        String usage();

        String helpGroup();

        String description();

        boolean showInHelp();

        Collection<String> aliases();

        String permission();

        void execute(CommandSender sender, List<String> arguments, String label);

        List<String> tabComplete(CommandSender sender, List<String> arguments, String label);
    }

    private record PermissionBinding(String value, Method dynamicMethod) {
    }

    private static final class RouteNode {

        private final Map<String, RouteNode> children = new LinkedHashMap<>();
        private CommandNode command;
    }

    private record RouteMatch(CommandNode command, int consumedTokens) {
    }

    private static final class AnnotatedCommandNode implements CommandNode {

        private final Object component;
        private final Method handler;
        private final Method completer;
        private final CommandHandler metadata;
        private final String name;
        private final String usage;
        private final String permission;
        private final String rootCommand;
        private final Method permissionMethod;

        private AnnotatedCommandNode(
                Object component,
                Method handler,
                Method completer,
                CommandHandler metadata,
                String name,
                String usage,
                PermissionBinding permissionBinding,
                String rootCommand
        ) {
            this.component = component;
            this.handler = handler;
            this.completer = completer;
            this.metadata = metadata;
            this.name = name;
            this.usage = usage;
            this.permission = permissionBinding.value();
            this.rootCommand = rootCommand;
            this.permissionMethod = permissionBinding.dynamicMethod();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String usage() {
            return usage;
        }

        @Override
        public String helpGroup() {
            return metadata.helpGroup();
        }

        @Override
        public String description() {
            return metadata.description().trim();
        }

        @Override
        public boolean showInHelp() {
            return metadata.showInHelp();
        }

        @Override
        public Collection<String> aliases() {
            return List.of(metadata.aliases());
        }

        @Override
        public String permission() {
            if (permissionMethod == null) return permission;
            try {
                Object value = permissionMethod.invoke(component);
                if (!(value instanceof String string)) {
                    throw new IllegalStateException("Dynamic command permission returned a non-string");
                }
                return string.trim();
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot resolve command permission", exception);
            }
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments, String label) {
            try {
                handler.invoke(component, new CommandContext(sender, rootCommand, label, usage, arguments));
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot invoke @CommandHandler: " + handler, exception);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> tabComplete(CommandSender sender, List<String> arguments, String label) {
            if (completer == null) return List.of();
            try {
                Object value = completer.invoke(
                        component,
                        new CommandContext(sender, rootCommand, label, usage, arguments)
                );
                if (value == null) return List.of();
                return List.copyOf((List<String>) value);
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot invoke @CommandCompleter: " + completer, exception);
            }
        }
    }
}
