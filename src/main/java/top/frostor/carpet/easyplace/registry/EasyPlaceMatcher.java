package top.frostor.carpet.easyplace.registry;

@FunctionalInterface
public interface EasyPlaceMatcher {
    boolean matches(EasyPlaceRestoreContext context);
}
