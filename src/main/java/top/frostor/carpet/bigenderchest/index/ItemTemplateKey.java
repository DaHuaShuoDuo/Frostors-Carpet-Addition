package top.frostor.carpet.bigenderchest.index;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Identity of an item template. Stack size and storage location are deliberately excluded.
 */
public final class ItemTemplateKey implements Comparable<ItemTemplateKey> {
    private final ItemStack template;
    private final String registryName;
    private final String componentKey;

    private ItemTemplateKey(ItemStack template) {
        this.template = template.copyWithCount(1);
        Identifier identifier = BuiltInRegistries.ITEM.getKey(template.getItem());
        this.registryName = identifier == null
                ? template.getItem().getDescriptionId()
                : identifier.toString();
        this.componentKey = template.getComponentsPatch().toString();
    }

    public static ItemTemplateKey of(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.isEmpty() ? null : new ItemTemplateKey(stack);
    }

    public ItemStack template() {
        return template.copy();
    }

    public String registryName() {
        return registryName;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(template, stack);
    }

    @Override
    public int compareTo(ItemTemplateKey other) {
        int name = registryName.compareTo(other.registryName);
        return name != 0 ? name : componentKey.compareTo(other.componentKey);
    }

    @Override
    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof ItemTemplateKey key)) return false;
        return template.getItem() == key.template.getItem()
                && template.getComponentsPatch().equals(key.template.getComponentsPatch());
    }

    @Override
    public int hashCode() {
        return 31 * template.getItem().hashCode() + template.getComponentsPatch().hashCode();
    }

    @Override
    public String toString() {
        return componentKey.isEmpty() ? registryName : registryName + componentKey;
    }
}
