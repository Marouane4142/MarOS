package io.github.marouane4142.maros;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** One consistent catalog for TV, companion and editors, including sideloaded apps. */
public final class AppCatalog {
    private AppCatalog() { }
    public static List<ResolveInfo> query(Context context) {
        LinkedHashMap<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(context.getPackageName())) found.putIfAbsent(pkg, info);
            }
        }
        ArrayList<ResolveInfo> result = new ArrayList<>(found.values());
        result.sort((a,b) -> String.valueOf(a.loadLabel(context.getPackageManager()))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(context.getPackageManager()))));
        return result;
    }
}
