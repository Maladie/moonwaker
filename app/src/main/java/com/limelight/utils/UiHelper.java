package com.limelight.utils;

import android.app.Activity;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.Locale;

public class UiHelper {

    private static final int TV_VERTICAL_PADDING_DP = 15;
    private static final int TV_HORIZONTAL_PADDING_DP = 15;

    private static void setGameModeStatus(Context context, boolean streaming, boolean interruptible) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GameManager gameManager = context.getSystemService(GameManager.class);

            if (streaming) {
                gameManager.setGameState(new GameState(false, interruptible ? GameState.MODE_GAMEPLAY_INTERRUPTIBLE : GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE));
            }
            else {
                gameManager.setGameState(new GameState(false, GameState.MODE_NONE));
            }
        }
    }

    public static void notifyStreamConnecting(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamConnected(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnteringPiP(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamExitingPiP(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnded(Context context) {
        setGameModeStatus(context, false, false);
    }

    /**
     * Android 13+ owns the per-app language ({@link LocaleManager}); earlier releases apply the
     * saved preference to each Activity's resources here.
     */
    public static void setLocale(Activity activity)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return;
        String locale = PreferenceConfiguration.readPreferences(activity).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.locale = new Locale(locale);
            activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        }
    }

    /** The saved app language tag, or {@link PreferenceConfiguration#DEFAULT_LANGUAGE}. */
    public static String currentLanguage(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleList locales = activity.getSystemService(LocaleManager.class).getApplicationLocales();
            return locales.isEmpty() ? PreferenceConfiguration.DEFAULT_LANGUAGE
                    : locales.get(0).getLanguage();
        }
        return PreferenceConfiguration.readPreferences(activity).language;
    }

    /** Applies and persists {@code language} now; the Activity restarts in the new language. */
    public static void applyLanguage(Activity activity, String language) {
        boolean system = language.equals(PreferenceConfiguration.DEFAULT_LANGUAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system persists the choice and recreates the Activity itself.
            activity.getSystemService(LocaleManager.class).setApplicationLocales(
                    system ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(language));
        } else {
            PreferenceConfiguration.saveLanguage(activity, language);
            activity.recreate();
        }
    }

    public static void notifyNewRootView(final Activity activity)
    {
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Allow this non-streaming activity to layout under notches.
            //
            // We should NOT do this for the Game activity unless
            // the user specifically opts in, because it can obscure
            // parts of the streaming surface.
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Draw under the status bar on Android Q devices

            // Using getDecorView() here breaks the translucent status/navigation bar when gestures are disabled
            activity.findViewById(android.R.id.content).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    // Use the tappable insets so we can draw under the status bar in gesture mode
                    Insets tappableInsets = windowInsets.getTappableElementInsets();
                    view.setPadding(tappableInsets.left,
                            tappableInsets.top,
                            tappableInsets.right,
                            0);

                    // Show a translucent navigation bar if we can't tap there
                    if (tappableInsets.bottom != 0) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                    }
                    else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                    }

                    return windowInsets;
                }
            });

            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
