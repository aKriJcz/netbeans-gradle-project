package org.netbeans.gradle.project.properties.ui;

import org.junit.Test;
import org.netbeans.gradle.project.api.config.ActiveSettingsQuery;
import org.netbeans.gradle.project.api.config.PropertyReference;
import org.netbeans.gradle.project.java.JavaExtensionDef;
import org.netbeans.gradle.project.java.properties.JavaProjectProperties;
import org.netbeans.gradle.project.properties.ExtensionActiveSettingsQuery;
import org.netbeans.gradle.project.properties.NbGradleCommonProperties;
import org.netbeans.gradle.project.properties.global.CommonGlobalSettings;
import org.netbeans.gradle.project.properties.global.GlobalSettingsPage;
import org.netbeans.gradle.project.properties.global.JavaSourcesDisplayMode;
import org.netbeans.gradle.project.util.NbConsumer;
import org.netbeans.gradle.project.util.NbSupplier;

public class AppearancePanelTest {
    private static PropertyReference<String> displayNamePattern(CommonGlobalSettings input) {
        return NbGradleCommonProperties.displayNamePattern(input.getActiveSettingsQuery());
    }

    private static NbSupplier<GlobalSettingsPage> settingsPageFactory() {
        return new NbSupplier<GlobalSettingsPage>() {
            @Override
            public GlobalSettingsPage get() {
                return AppearancePanel.createSettingsPage(false);
            }
        };
    }

    private static PropertyReference<JavaSourcesDisplayMode> javaSourcesDisplayMode(CommonGlobalSettings input) {
        ActiveSettingsQuery javaExtQuery = new ExtensionActiveSettingsQuery(
                input.getActiveSettingsQuery(),
                JavaExtensionDef.EXTENSION_NAME);

        return JavaProjectProperties.javaSourcesDisplayMode(javaExtQuery);
    }

    @Test
    public void testInitAndReadBack1() throws Exception {
        GlobalSettingsPanelTestUtils.testGlobalInitAndReadBack(settingsPageFactory(), new NbConsumer<CommonGlobalSettings>() {
            @Override
            public void accept(CommonGlobalSettings input) {
                displayNamePattern(input).setValue("${project.name}");
                javaSourcesDisplayMode(input).setValue(JavaSourcesDisplayMode.GROUP_BY_SOURCESET);
            }
        });
    }

    @Test
    public void testInitAndReadBack2() throws Exception {
        GlobalSettingsPanelTestUtils.testGlobalInitAndReadBack(settingsPageFactory(), new NbConsumer<CommonGlobalSettings>() {
            @Override
            public void accept(CommonGlobalSettings input) {
                displayNamePattern(input).setValue("${project.path}-${project.version}-test");
                javaSourcesDisplayMode(input).setValue(JavaSourcesDisplayMode.GROUP_BY_SOURCESET);
            }
        });
    }

    @Test
    public void testInitAndReadBack3() throws Exception {
        GlobalSettingsPanelTestUtils.testGlobalInitAndReadBack(settingsPageFactory(), new NbConsumer<CommonGlobalSettings>() {
            @Override
            public void accept(CommonGlobalSettings input) {
                displayNamePattern(input).setValue("${project.path}");
                javaSourcesDisplayMode(input).setValue(JavaSourcesDisplayMode.DEFAULT_MODE);
            }
        });
    }
}
