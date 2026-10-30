package cn.mythicland.dreamlib.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Annotation-driven base view for small inventory menus.
 *
 * <p>Subclasses only implement {@link #title(Player)} and {@link #size(Player)}, then annotate
 * item-rendering methods with {@link MenuButton} and click handlers with {@link MenuAction}. This
 * keeps menu layout declarations close to their behavior while retaining the shared Lib menu
 * lifecycle.</p>
 */
public abstract class AnnotatedMenuView implements StatefulMenuView {

    private final List<ButtonDefinition> buttons;
    private final Map<Integer, List<ActionDefinition>> actions;

    /**
     * Discovers the annotated methods on the concrete view.
     */
    protected AnnotatedMenuView() {
        Definitions definitions = discoverDefinitions(getClass());
        this.buttons = definitions.buttons();
        this.actions = definitions.actions();
    }

    private static Definitions discoverDefinitions(Class<?> concreteType) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = concreteType;
             type != null && type != AnnotatedMenuView.class;
             type = type.getSuperclass()) {
            methods.addAll(Arrays.asList(type.getDeclaredMethods()));
        }
        methods.sort(Comparator.comparing(AnnotatedMenuView::methodKey));

        List<ButtonDefinition> buttons = new ArrayList<>();
        Map<Integer, List<ActionDefinition>> actions = new HashMap<>();
        Set<Integer> buttonSlots = new HashSet<>();
        for (Method method : methods) {
            MenuButton button = method.getAnnotation(MenuButton.class);
            if (button != null) {
                validateMethod(method, "button");
                validateSlot(button.slot(), method);
                if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                    throw invalidMethod(method, "menu button must return ItemStack");
                }
                if (!buttonSlots.add(button.slot())) {
                    throw new IllegalStateException("Duplicate menu button slot: " + button.slot());
                }
                validateButtonParameters(method);
                makeAccessible(method);
                buttons.add(new ButtonDefinition(button.slot(), method));
            }

            MenuAction action = method.getAnnotation(MenuAction.class);
            if (action != null) {
                validateMethod(method, "action");
                validateSlot(action.slot(), method);
                Class<?> returnType = method.getReturnType();
                if (returnType != void.class && returnType != boolean.class && returnType != Boolean.class) {
                    throw invalidMethod(method, "menu action must return void or boolean");
                }
                validateActionParameters(method);
                makeAccessible(method);
                ActionDefinition definition = new ActionDefinition(
                        action.slot(),
                        Set.of(action.clicks()),
                        method,
                        action.playClickSound()
                );
                List<ActionDefinition> slotActions = actions.computeIfAbsent(
                        action.slot(),
                        ignored -> new ArrayList<>()
                );
                for (ActionDefinition existing : slotActions) {
                    if (existing.overlaps(definition)) {
                        throw new IllegalStateException("Overlapping menu actions for slot: " + action.slot());
                    }
                }
                slotActions.add(definition);
            }
        }
        Map<Integer, List<ActionDefinition>> immutableActions = new HashMap<>();
        for (Map.Entry<Integer, List<ActionDefinition>> entry : actions.entrySet()) {
            immutableActions.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        buttons.sort(Comparator.comparingInt(ButtonDefinition::slot));
        return new Definitions(List.copyOf(buttons), Map.copyOf(immutableActions));
    }

    private static void validateMethod(Method method, String kind) {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) {
            throw invalidMethod(method, kind + " method must be an instance method");
        }
    }

    private static void validateSlot(int slot, Method method) {
        if (slot < 0) throw invalidMethod(method, "menu slot cannot be negative: " + slot);
    }

    private static void validateButtonParameters(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length > 1 || (parameters.length == 1 && parameters[0] != Player.class)) {
            throw invalidMethod(method, "menu button parameters must be empty or (Player)");
        }
    }

    private static void validateActionParameters(Method method) {
        Set<Class<?>> supported = Set.of(Player.class, ClickType.class, InventoryClickEvent.class, MenuService.class);
        for (Class<?> parameter : method.getParameterTypes()) {
            if (!supported.contains(parameter)) {
                throw invalidMethod(method, "unsupported menu action parameter: " + parameter.getName());
            }
        }
    }

    private static Object actionArgument(
            Class<?> type,
            Player player,
            InventoryClickEvent event,
            MenuService menuService
    ) {
        if (type == Player.class) return player;
        if (type == ClickType.class) return event.getClick();
        if (type == InventoryClickEvent.class) return event;
        if (type == MenuService.class) return menuService;
        throw new IllegalStateException("Unsupported annotated menu action parameter: " + type.getName());
    }

    private static void makeAccessible(Method method) {
        if (!method.trySetAccessible()) throw invalidMethod(method, "cannot access annotated method");
    }

    private static String methodKey(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static IllegalStateException invalidMethod(Method method, String message) {
        return new IllegalStateException(message + ": " + method);
    }

    private static IllegalStateException invocationFailure(String kind, Method method, Throwable failure) {
        return new IllegalStateException("Annotated menu " + kind + " failed: " + method, failure);
    }

    @Override
    public final void render(Player player, Inventory inventory) {
        inventory.clear();
        for (ButtonDefinition button : buttons) {
            if (button.slot() >= inventory.getSize()) {
                throw new IllegalStateException(
                        "Annotated menu button slot exceeds inventory size: " + button.slot()
                );
            }
            ItemStack item = invokeButton(button, player);
            inventory.setItem(button.slot(), item);
        }
    }

    @Override
    public final void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size(player)) return;
        List<ActionDefinition> definitions = actions.get(slot);
        if (definitions == null) return;
        for (ActionDefinition action : definitions) {
            if (action.accepts(event.getClick())) {
                invokeAction(action, player, event, menuService);
                return;
            }
        }
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuService menuService) {
        event.setCancelled(true);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        // Stateless menus do not need close handling.
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        onClose(player, inventory);
    }

    private ItemStack invokeButton(ButtonDefinition definition, Player player) {
        try {
            Object result = definition.method().getParameterCount() == 0
                    ? definition.method().invoke(this)
                    : definition.method().invoke(this, player);
            if (result == null) return null;
            if (!(result instanceof ItemStack item)) {
                throw invalidMethod(definition.method(), "menu button returned a non-ItemStack value");
            }
            return item;
        } catch (InvocationTargetException exception) {
            throw invocationFailure("button", definition.method(), exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw invocationFailure("button", definition.method(), exception);
        }
    }

    private void invokeAction(
            ActionDefinition definition,
            Player player,
            InventoryClickEvent event,
            MenuService menuService
    ) {
        Object[] arguments = Arrays.stream(definition.method().getParameterTypes())
                .map(type -> actionArgument(type, player, event, menuService))
                .toArray();
        try {
            Object result = definition.method().invoke(this, arguments);
            boolean successful = !(result instanceof Boolean value) || value;
            if (successful && definition.playClickSound()) MenuSelection.playClickSound(player);
        } catch (InvocationTargetException exception) {
            throw invocationFailure("action", definition.method(), exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw invocationFailure("action", definition.method(), exception);
        }
    }

    private record Definitions(List<ButtonDefinition> buttons, Map<Integer, List<ActionDefinition>> actions) {
    }

    private record ButtonDefinition(int slot, Method method) {
    }

    private record ActionDefinition(int slot, Set<ClickType> clicks, Method method, boolean playClickSound) {

        private boolean accepts(ClickType click) {
            return clicks.isEmpty() || clicks.contains(click);
        }

        private boolean overlaps(ActionDefinition other) {
            if (acceptsAll() || other.acceptsAll()) return true;
            return clicks.stream().anyMatch(other.clicks()::contains);
        }

        private boolean acceptsAll() {
            return clicks.isEmpty();
        }
    }
}
