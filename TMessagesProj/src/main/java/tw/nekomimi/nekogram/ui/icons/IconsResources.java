package tw.nekomimi.nekogram.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import android.os.Build;

import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.telegram.messenger.FileLog;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    private int _iconsType = -1;

    // Cache ConstantState by convertedId to avoid redundant native loads.
    // All _solar drawables are pure <vector>, so density is irrelevant for cache key.
    private final ConcurrentHashMap<Integer, Drawable.ConstantState> constantStateCache =
            new ConcurrentHashMap<>();

    // Per-id lock to serialize native loads of the same convertedId across threads,
    // preventing Android's mActiveLoadingDrawables from seeing a concurrent load as recursion.
    private final ConcurrentHashMap<Integer, ReentrantLock> perIdLocks =
            new ConcurrentHashMap<>();

    // Thread-local re-entrancy guard: detects same-thread recursive getDrawable calls
    // (e.g. RippleDrawable inflate triggering nested getDrawable on the same Resources instance).
    private final ThreadLocal<HashSet<Integer>> loadingStack =
            ThreadLocal.withInitial(HashSet::new);

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return loadCached(id, (convertedId) -> super.getDrawable(convertedId, null));
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return loadCached(id, (convertedId) -> super.getDrawable(convertedId, theme));
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        return loadCached(id, (convertedId) -> super.getDrawableForDensity(convertedId, density, theme));
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return loadCached(id, (convertedId) -> super.getDrawableForDensity(convertedId, density, null));
    }

    private interface DrawableLoader {
        Drawable load(int convertedId) throws NotFoundException;
    }

    private Drawable loadCached(int originalId, DrawableLoader loader) throws NotFoundException {
        int convertedId = getConversion(originalId);

        // If conversion is a no-op, bypass all caching / locking overhead.
        if (convertedId == originalId) {
            return loader.load(originalId);
        }

        // Layer 1: Re-entrancy guard (same thread, nested inflate).
        // If this thread is already loading this originalId, skip conversion to break the cycle.
        HashSet<Integer> stack = loadingStack.get();
        if (!stack.add(originalId)) {
            return loader.load(originalId);
        }

        try {
            // Layer 2: ConstantState cache hit — produce a new Drawable instance without
            // touching the native Resources loader (and therefore mActiveLoadingDrawables).
            Drawable.ConstantState cached = constantStateCache.get(convertedId);
            if (cached != null) {
                Drawable d = cached.newDrawable(this);
                if (d != null) return d;
            }

            // Layer 3: Per-id lock — serialize concurrent loads of the same convertedId
            // so only one thread enters the native loader at a time for a given resource.
            ReentrantLock lock = perIdLocks.computeIfAbsent(convertedId, k -> new ReentrantLock());
            lock.lock();
            try {
                // Double-check: another thread may have populated the cache while we waited.
                cached = constantStateCache.get(convertedId);
                if (cached != null) {
                    Drawable d = cached.newDrawable(this);
                    if (d != null) return d;
                }

                Drawable result = loader.load(convertedId);
                if (result != null) {
                    Drawable.ConstantState state = result.getConstantState();
                    if (state != null) {
                        constantStateCache.put(convertedId, state);
                    }
                }
                return result;
            } catch (Exception e) {
                // Layer 4: Last-resort fallback — if the system still throws
                // "Recursive reference in drawable", degrade to the original (non-solar) icon.
                FileLog.e("IconsResources: recursive reference guard triggered for id=" + originalId + " api=" + Build.VERSION.SDK_INT, e);
                try {
                    FirebaseCrashlytics.getInstance().log("IconsResources_recursive_guard api=" + Build.VERSION.SDK_INT + " id=" + originalId + " err=" + e.getMessage());
                } catch (Throwable ignore) {
                }
                try {
                    return loader.load(originalId);
                } catch (Exception fallbackFailed) {
                    FileLog.e("IconsResources: fallback also failed for id=" + originalId, fallbackFailed);
                    return null;
                }
            } finally {
                lock.unlock();
            }
        } finally {
            stack.remove(originalId);
        }
    }

    private int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    private int getConversion(int icon, int forcedIconsType) {
        if (_iconsType == -1) {
            _iconsType = NaConfig.INSTANCE.getIconReplacements().Int();
        }

        int consideredIconsType = forcedIconsType == -1 ? _iconsType : forcedIconsType;

        if (consideredIconsType == ICON_REPLACE_SOLAR) {
            return SolarIcons.Companion.getConversion(icon);
        }

        return icon;
    }
}
