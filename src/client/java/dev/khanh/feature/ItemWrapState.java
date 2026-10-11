package dev.khanh.feature;

/** Mixed into ItemRenderState: whether the item being drawn gets the Custom Glint wrap. */
public interface ItemWrapState {
    /** Set while a wrapped ItemRenderState submits its layers (render-thread only). */
    ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    void khanh$setWrap(boolean wrap);
}
