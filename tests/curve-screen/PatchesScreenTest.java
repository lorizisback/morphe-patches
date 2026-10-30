package app.local.curve.screen.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.Set;

/** Runs only on the disposable Morphe emulator; reads no account preferences. */
public final class PatchesScreenTest extends Instrumentation {
    private static final String PACKAGE = "com.imaginecurve.curve.prd";
    private static final String SCREEN = "app.local.extension.curve.PatchesActivity";
    private static final String LAUNCH = "com.curve.startup.launch.LaunchActivity";
    private Bundle arguments;
    private Activity current;
    private SharedPreferences preferences;
    private Class<?> settings;
    private boolean cringeIncluded;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            require(arguments != null && "morphe_sample_api36".equals(arguments.getString("avd")),
                "Run only after verifying the morphe_sample_api36 AVD; pass -e avd morphe_sample_api36");
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE),
                "Refusing to run on a physical device");
            runChecks();
            result.putString("stream", "PASS Patches screen: Curve components, checklist/reset/protected Patches, draft recreation, toolbar/system Back, atomic apply and relaunch; original preference restored\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL Patches screen: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void runChecks() throws Exception {
        Context context = getTargetContext();
        settings = context.getClassLoader().loadClass("app.local.extension.curve.PatchSettings");
        preferences = context.getSharedPreferences("morphe_patches", Context.MODE_PRIVATE);
        Map<String, ?> originalSettings = new HashMap<>(preferences.getAll());
        cringeIncluded = (Boolean) context.getClassLoader().loadClass("app.local.extension.curve.RemoveCringe")
            .getMethod("isIncluded").invoke(null);
        require(cringeIncluded == Boolean.parseBoolean(arguments.getString("cringe", "false")), "Wrong patch variant installed");
        boolean original = preferences.getBoolean("hide_flex", true);
        try {
            require(preferences.edit().putBoolean("hide_launchpad", false)
                .putStringSet("hidden_launchpad_tiles", Collections.emptySet()).putBoolean("remove_cringe", true).commit(), "Could not set fixture settings");
            settings.getMethod("initialize", Context.class).invoke(null, context);
            Class<?> action = context.getClassLoader().loadClass("com.curve.launchpad.model.LaunchpadAction");
            java.lang.reflect.Constructor<?> button = context.getClassLoader()
                .loadClass("com.curve.launchpad.model.LaunchpadItem$Button")
                .getConstructor(String.class, String.class, String.class, String.class, action);
            Object none = action.getField("NONE").get(null);
            context.getClassLoader().loadClass("app.local.extension.curve.LaunchpadTiles")
                .getMethod("filter", java.util.List.class).invoke(null, Arrays.asList(
                    button.newInstance("First fixture tile", "fixture://icon", null, "a", none),
                    button.newInstance("Second fixture tile", "fixture://icon", null, "b", none),
                    button.newInstance("Patches", "fixture://icon", null, "morphe_patches", none)));
            ActivityInfo info = context.getPackageManager().getActivityInfo(new ComponentName(PACKAGE, SCREEN), 0);
            require(!info.exported, "Patches screen must not be exported");
            require("style/Curve.AppTheme.NoActionBar".equals(
                context.getResources().getResourceTypeName(info.getThemeResource()) + "/"
                    + context.getResources().getResourceEntryName(info.getThemeResource())),
                "Patches screen must use the existing Curve theme");

            open();
            if (arguments.containsKey("capture")) {
                java.io.File directory = getTargetContext().getExternalFilesDir(null);
                require(directory != null, "Screenshot directory unavailable");
                java.io.File file = new java.io.File(directory, arguments.getString("capture") + ".png");
                android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
                require(bitmap != null, "Screenshot unavailable");
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Screenshot failed");
                } finally { bitmap.recycle(); }
            }
            if (cringeIncluded) {
                onUi(() -> RemoveCringeChecks.run(current, settings, preferences));
                require(preferences.edit().putBoolean("remove_cringe", true).commit(), "Could not restore Cringe fixture");
                settings.getMethod("initialize", Context.class).invoke(null, context);
            }
            onUi(() -> {
                requireClass(view("morphe_patches_toolbar"), "com.curve.design.toolbar.CrvToolbar");
                requireClass(view("morphe_patches_row"), "com.curve.design.listItem.ListItem");
                requireClass(view("morphe_patches_toggle"), "com.curve.design.listItem.ListItemToggle");
                requireClass(view("morphe_patches_actions"), "com.curve.design.buttons.ButtonStack");
                requireClass(view("morphe_patches_apply"), "com.curve.design.buttons.CrvButton");
                requireClass(toggle(), "com.google.android.material.materialswitch.MaterialSwitch");
                require(view("morphe_cringe_panel").getVisibility() == (cringeIncluded ? View.VISIBLE : View.GONE),
                    "Remove Cringe must appear only when included");
                require(!view("morphe_patches_apply").isEnabled(), "Apply must start disabled");
                if (cringeIncluded) {
                    require(cringe().isChecked(), "Remove Cringe should default on");
                    requireClass(cringe(), "com.google.android.material.materialswitch.MaterialSwitch");
                    cringe().toggle();
                    require(view("morphe_patches_apply").isEnabled(), "Cringe draft must enable Apply");
                    require(preferences.getBoolean("remove_cringe", true), "Cringe draft saved before Apply");
                }
                require(toggle().isChecked() == original, "Screen must load the saved value");
                require(view("morphe_patches_row").performClick(), "Setting row must be clickable");
                require(toggle().isChecked() != original, "Row click must toggle the draft");
                require(view("morphe_patches_apply").isEnabled(), "Changed draft must enable Apply");
                require(!master().isChecked(), "Launchpad master must default off");
                require(choices().getChildCount() == 2, "Checklist must exclude Patches");
                requireClass(choice(0), "com.curve.design.checkbox.CrvCheckBox");
                require("First fixture tile".contentEquals(choice(0).getContentDescription()), "Checkbox must have a label");
                master().toggle(); choice(0).toggle();
                require(view("morphe_launchpad_reset").isEnabled(), "Selection must enable Reset");
                require(!preferences.getBoolean("hide_launchpad", false)
                    && preferences.getStringSet("hidden_launchpad_tiles", Collections.emptySet()).isEmpty(),
                    "Draft wrote preferences before Apply");
            });
            requireStored(original);

            Activity previous = current;
            ActivityMonitor recreation = addMonitor(SCREEN, null, false);
            try {
                onUi(previous::recreate);
                current = waitForMonitorWithTimeout(recreation, 10000);
                require(current != null && current != previous, "Activity did not recreate");
                waitForIdleSync();
            } finally {
                removeMonitor(recreation);
            }
            onUi(() -> {
                require(toggle().isChecked() != original, "Recreation lost the draft");
                if (cringeIncluded) require(!cringe().isChecked(), "Recreation lost Cringe draft");
                require(master().isChecked() && choice(0).isChecked(), "Recreation lost Launchpad draft");
                require(view("morphe_patches_apply").isEnabled(), "Recreated draft must enable Apply");
                View toolbar = view("morphe_patches_toolbar");
                CharSequence description = (CharSequence) toolbar.getClass()
                    .getMethod("getNavigationContentDescription").invoke(toolbar);
                ImageButton back = navigationButton(toolbar, description);
                require(back != null && back.performClick(), "Toolbar Back must be accessible and clickable");
                require(current.isFinishing(), "Toolbar Back must close Patches");
            });
            requireStored(original);

            open();
            onUi(() -> {
                require(toggle().isChecked() == original, "Toolbar Back saved a canceled draft");
                if (cringeIncluded) {
                    require(cringe().isChecked(), "Toolbar Back saved Cringe draft");
                    cringe().toggle();
                }
                require(!master().isChecked() && !choice(0).isChecked(), "Back saved Launchpad draft");
                toggle().toggle();
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            onUi(() -> require(current.isFinishing(), "System Back must close Patches"));
            requireStored(original);

            open();
            ActivityMonitor relaunch = addMonitor(LAUNCH, null, false);
            try {
                onUi(() -> {
                    require(toggle().isChecked() == original, "System Back saved a canceled draft");
                    choice(0).toggle();
                    require(view("morphe_launchpad_reset").performClick(), "Reset not clickable");
                    require(!choice(0).isChecked() && !choice(1).isChecked(), "Reset did not clear checklist");
                    require(!view("morphe_launchpad_reset").isEnabled(), "Empty selection must disable Reset");
                    require(!view("morphe_patches_apply").isEnabled(), "Reset back to saved state must disable Apply");
                    master().toggle(); choice(1).toggle(); toggle().toggle();
                    if (cringeIncluded) {
                        require(cringe().isChecked(), "System Back saved Cringe draft");
                        cringe().toggle();
                    }
                    require(view("morphe_patches_apply").performClick(), "Apply must be clickable");
                });
                require(waitForMonitorWithTimeout(relaunch, 10000) != null, "Apply did not relaunch Curve");
                requireStored(!original);
                require(preferences.getBoolean("remove_cringe", true) != cringeIncluded, "Apply did not save Cringe choice");
                require(preferences.getBoolean("hide_launchpad", false)
                    && preferences.getStringSet("hidden_launchpad_tiles", Collections.emptySet())
                        .equals(Collections.singleton("b")), "Apply did not atomically save Launchpad choices");
                onUi(() -> require(current.isFinishing(), "Apply must close Patches"));
            } finally {
                removeMonitor(relaunch);
            }
        } finally {
            try {
                if (current != null) onUi(() -> current.finish());
            } finally {
                SharedPreferences.Editor editor = preferences.edit().clear();
                for (Map.Entry<String, ?> entry : originalSettings.entrySet()) {
                    if (entry.getValue() instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                    else editor.putStringSet(entry.getKey(), new java.util.HashSet<>((Set<String>) entry.getValue()));
                }
                require(editor.commit(), "Could not restore the original patch preference");
                settings.getMethod("initialize", Context.class).invoke(null, context);
            }
        }
    }

    private void open() {
        current = startActivitySync(new Intent().setClassName(PACKAGE, SCREEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }

    private View view(String name) {
        int id = current.getResources().getIdentifier(name, "id", PACKAGE);
        require(id != 0, "Missing screen resource: " + name);
        View view = current.findViewById(id);
        require(view != null, "Missing screen view: " + name);
        return view;
    }

    private CompoundButton toggle() {
        return (CompoundButton) view("swiToggle");
    }

    private CompoundButton master() {
        return (CompoundButton) view("morphe_launchpad_row").findViewById(
            current.getResources().getIdentifier("swiToggle", "id", PACKAGE));
    }

    private CompoundButton cringe() {
        return (CompoundButton) view("morphe_cringe_row").findViewById(
            current.getResources().getIdentifier("swiToggle", "id", PACKAGE));
    }

    private ViewGroup choices() {
        return (ViewGroup) view("morphe_launchpad_choices").findViewById(
            current.getResources().getIdentifier("linear_layout", "id", PACKAGE));
    }

    private CompoundButton choice(int index) {
        return (CompoundButton) choices().getChildAt(index).findViewById(
            current.getResources().getIdentifier("swiCheck", "id", PACKAGE));
    }

    private void requireStored(boolean expected) throws Exception {
        require(preferences.getBoolean("hide_flex", true) == expected, "Unexpected persisted Hide Flex value");
        require(((Boolean) settings.getMethod("isFlexHidden").invoke(null)) == expected,
            "Unexpected active Hide Flex value");
    }

    private static ImageButton navigationButton(View view, CharSequence description) {
        if (view instanceof ImageButton && description != null
                && description.toString().contentEquals(view.getContentDescription() == null
                    ? "" : view.getContentDescription())) return (ImageButton) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                ImageButton result = navigationButton(group.getChildAt(index), description);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void onUi(UiAction action) throws Exception {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] instanceof Exception) throw (Exception) failure[0];
        if (failure[0] instanceof Error) throw (Error) failure[0];
    }

    private interface UiAction { void run() throws Exception; }

    private static void requireClass(Object object, String name) {
        require(object.getClass().getName().equals(name), "Expected Curve component: " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
