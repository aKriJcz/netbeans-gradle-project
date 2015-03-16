package org.netbeans.gradle.project.properties;

import java.awt.Dialog;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jtrim.utils.ExceptionHelper;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.platform.JavaPlatformManager;
import org.netbeans.api.java.platform.Specification;
import org.netbeans.gradle.project.NbGradleProject;
import org.netbeans.gradle.project.NbStrings;
import org.netbeans.gradle.project.api.entry.GradleProjectPlatformQuery;
import org.netbeans.gradle.project.api.entry.ProjectPlatform;
import org.netbeans.gradle.project.properties2.ActiveSettingsQuery;
import org.netbeans.gradle.project.properties2.NbGradleCommonProperties;
import org.netbeans.gradle.project.properties2.PropertyReference;
import org.netbeans.gradle.project.properties2.standard.GradleLocationProperty;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Lookup;

@SuppressWarnings("serial")
public class CommonProjectPropertiesPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(CommonProjectPropertiesPanel.class.getName());

    private final NbGradleProject project;
    private String currentProfileName;
    private ActiveSettingsQuery currentSettings;
    private PropertyValues currentValues;

    private CommonProjectPropertiesPanel(NbGradleProject project) {
        this.project = project;
        this.currentSettings = null;
        this.currentProfileName = null;

        initComponents();

        setupEnableDisable();
    }

    public static ProfileBasedPanel createProfileBasedPanel(final NbGradleProject project) {
        ExceptionHelper.checkNotNullArgument(project, "project");

        final CommonProjectPropertiesPanel customPanel = new CommonProjectPropertiesPanel(project);
        return ProfileBasedPanel.createPanel(project, customPanel, new ProfileValuesEditorFactory() {
            @Override
            public ProfileValuesEditor startEditingProfile(String displayName, ActiveSettingsQuery profileQuery) {
                PropertyValues currentValues = customPanel.new PropertyValues(project, profileQuery);

                customPanel.currentProfileName = displayName;
                customPanel.currentSettings = profileQuery;
                customPanel.currentValues = currentValues;

                return currentValues;
            }
        });
    }

    private static <Value> Value setInheritAndGetValue(
            Value value,
            PropertyReference<? extends Value> valueWitFallbacks,
            JCheckBox inheritCheck) {
        inheritCheck.setSelected(value == null);
        return value != null ? value : valueWitFallbacks.getActiveValue();
    }

    private static void setupInheritCheck(final JCheckBox inheritCheck, JComponent... components) {
        final JComponent[] componentsSnapshot = components.clone();
        ChangeListener changeListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                boolean enable = !inheritCheck.isSelected();
                for (JComponent component: componentsSnapshot) {
                    component.setEnabled(enable);
                }
            }
        };

        changeListener.stateChanged(new ChangeEvent(inheritCheck));
        inheritCheck.getModel().addChangeListener(changeListener);
    }

    private void setupEnableDisable() {
        setupInheritCheck(jScriptPlatformInherit, jScriptPlatformCombo);
        setupInheritCheck(jGradleHomeInherit, jGradleHomeEdit);
        setupInheritCheck(jPlatformComboInherit, jPlatformCombo);
        setupInheritCheck(jSourceEncodingInherit, jSourceEncoding);
        setupInheritCheck(jSourceLevelComboInherit, jSourceLevelCombo);
    }

    private void fillProjectPlatformCombo() {
        List<ProjectPlatformComboItem> comboItems = new LinkedList<>();
        for (GradleProjectPlatformQuery query: Lookup.getDefault().lookupAll(GradleProjectPlatformQuery.class)) {
            for (ProjectPlatform platform: query.getAvailablePlatforms()) {
                comboItems.add(new ProjectPlatformComboItem(platform));
            }
        }

        jPlatformCombo.setModel(new DefaultComboBoxModel<>(comboItems.toArray(new ProjectPlatformComboItem[comboItems.size()])));
    }

    private void fillScriptPlatformCombo() {
        JavaPlatform[] platforms = JavaPlatformManager.getDefault().getInstalledPlatforms();
        List<JavaPlatformComboItem> comboItems = new LinkedList<>();

        for (JavaPlatform platform: GlobalGradleSettings.filterIndistinguishable(platforms)) {
            Specification specification = platform.getSpecification();
            if (specification != null && specification.getVersion() != null) {
                comboItems.add(new JavaPlatformComboItem(platform));
            }
        }

        jScriptPlatformCombo.setModel(new DefaultComboBoxModel<>(comboItems.toArray(new JavaPlatformComboItem[comboItems.size()])));
    }

    private void displayManageTasksPanel(String profileName, ActiveSettingsQuery settings) {
        ManageTasksPanel panel = new ManageTasksPanel(project);
        panel.initSettings(settings);

        DialogDescriptor dlgDescriptor = new DialogDescriptor(
                panel,
                NbStrings.getManageTasksDlgTitle(profileName),
                true,
                new Object[]{DialogDescriptor.OK_OPTION, DialogDescriptor.CANCEL_OPTION},
                DialogDescriptor.OK_OPTION,
                DialogDescriptor.BOTTOM_ALIGN,
                null,
                null);
        Dialog dlg = DialogDisplayer.getDefault().createDialog(dlgDescriptor);
        dlg.pack();
        dlg.setVisible(true);

        if (DialogDescriptor.OK_OPTION == dlgDescriptor.getValue()) {
            panel.saveTasks(settings);
        }
    }

    private void displayManageBuiltInTasksPanel(String profileName, ActiveSettingsQuery settings) {
        ManageBuiltInTasksPanel panel = new ManageBuiltInTasksPanel(project, settings);

        DialogDescriptor dlgDescriptor = new DialogDescriptor(
                panel,
                NbStrings.getManageBuiltInTasksDlgTitle(profileName),
                true,
                new Object[]{DialogDescriptor.OK_OPTION, DialogDescriptor.CANCEL_OPTION},
                DialogDescriptor.OK_OPTION,
                DialogDescriptor.BOTTOM_ALIGN,
                null,
                null);
        Dialog dlg = DialogDisplayer.getDefault().createDialog(dlgDescriptor);
        dlg.pack();
        dlg.setVisible(true);

        if (DialogDescriptor.OK_OPTION == dlgDescriptor.getValue()) {
            panel.saveModifiedTasks();
        }
    }

    private final class PropertyValues implements ProfileValuesEditor {
        public NbGradleCommonProperties commonProperties;

        public GradleLocation gradleLocation;
        public JavaPlatform scriptPlatform;
        public Charset sourceEncoding;
        public ProjectPlatform targetPlatform;
        public String sourceLevel;

        public PropertyValues(NbGradleProject ownerProject, ActiveSettingsQuery settings) {
            this(new NbGradleCommonProperties(ownerProject, settings));
        }

        public PropertyValues(NbGradleCommonProperties commonProperties) {
            this.commonProperties = commonProperties;

            this.gradleLocation = commonProperties.gradleLocation().tryGetValueWithoutFallback();
            this.scriptPlatform = commonProperties.scriptPlatform().tryGetValueWithoutFallback();
            this.sourceEncoding = commonProperties.sourceEncoding().tryGetValueWithoutFallback();
            this.targetPlatform = commonProperties.targetPlatform().tryGetValueWithoutFallback();
            this.sourceLevel = commonProperties.sourceLevel().tryGetValueWithoutFallback();
        }

        public void refreshPlatformCombos() {
            displayScriptPlatform();
            displayTargetPlatform();
        }

        @Override
        public void displayValues() {
            displayGradleLocation();
            displayScriptPlatform();
            displayTargetPlatform();
            displaySourceEncoding();
            displaySourceLevel();
        }

        @Override
        public void readFromGui() {
            String gradleHomeStr = jGradleHomeEdit.getText().trim();
            GradleLocation gradleHome = GradleLocationProperty.getGradleLocationFromString(gradleHomeStr);
            gradleLocation = jGradleHomeInherit.isSelected() ? null : gradleHome;

            JavaPlatformComboItem selectedScriptPlatform = (JavaPlatformComboItem)jScriptPlatformCombo.getSelectedItem();
            if (selectedScriptPlatform != null) {
                scriptPlatform = jScriptPlatformInherit.isSelected() ? null : selectedScriptPlatform.getPlatform();
            }

            ProjectPlatformComboItem selected = (ProjectPlatformComboItem)jPlatformCombo.getSelectedItem();
            if (selected != null) {
                targetPlatform = jPlatformComboInherit.isSelected() ? null : selected.getPlatform();
            }

            String charsetName = jSourceEncoding.getText().trim();
            try {
                Charset newEncoding = Charset.forName(charsetName);
                sourceEncoding = jSourceEncodingInherit.isSelected() ? null : newEncoding;
            } catch (IllegalCharsetNameException ex) {
                LOGGER.log(Level.INFO, "Illegal character set: " + charsetName, ex);
            } catch (UnsupportedCharsetException ex) {
                LOGGER.log(Level.INFO, "Unsupported character set: " + charsetName, ex);
            }

            sourceLevel = jSourceLevelComboInherit.isSelected() ? null : (String)jSourceLevelCombo.getSelectedItem();
        }

        @Override
        public void applyValues() {
            commonProperties.scriptPlatform().trySetValue(scriptPlatform);
            commonProperties.gradleLocation().trySetValue(gradleLocation);
            commonProperties.targetPlatform().trySetValue(targetPlatform);
            commonProperties.sourceEncoding().trySetValue(sourceEncoding);
            commonProperties.sourceLevel().trySetValue(sourceLevel);
        }

        private void displayGradleLocation() {
            GradleLocation value = setInheritAndGetValue(
                    gradleLocation,
                    commonProperties.gradleLocation(),
                    jGradleHomeInherit);

            if (value != null) {
                String gradleHome = GradleLocationProperty.gradleLocationToString(value);
                jGradleHomeEdit.setText(gradleHome);
            }
        }

        private void displayScriptPlatform() {
            JavaPlatform value = setInheritAndGetValue(
                    scriptPlatform,
                    commonProperties.scriptPlatform(),
                    jScriptPlatformInherit);

            fillScriptPlatformCombo();
            if (value != null) {
                jScriptPlatformCombo.setSelectedItem(new JavaPlatformComboItem(value));
            }
        }

        private void displayTargetPlatform() {
            ProjectPlatform value = setInheritAndGetValue(
                    targetPlatform,
                    commonProperties.targetPlatform(),
                    jPlatformComboInherit);
            fillProjectPlatformCombo();
            if (value != null) {
                jPlatformCombo.setSelectedItem(new ProjectPlatformComboItem(value));
            }
        }

        private void displaySourceEncoding() {
            Charset value = setInheritAndGetValue(
                    sourceEncoding,
                    commonProperties.sourceEncoding(),
                    jSourceEncodingInherit);
            if (value != null) {
                jSourceEncoding.setText(value.name());
            }
        }

        private void displaySourceLevel() {
            String value = setInheritAndGetValue(
                    sourceLevel,
                    commonProperties.sourceLevel(),
                    jSourceLevelComboInherit);
            if (value != null) {
                jSourceLevelCombo.setSelectedItem(value);
            }
        }
    }

    private static class JavaPlatformComboItem {
        private final JavaPlatform platform;

        public JavaPlatformComboItem(JavaPlatform platform) {
            ExceptionHelper.checkNotNullArgument(platform, "platform");
            this.platform = platform;
        }

        public JavaPlatform getPlatform() {
            return platform;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 41 * hash + (this.platform.getSpecification().getVersion().hashCode());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final JavaPlatformComboItem other = (JavaPlatformComboItem)obj;
            SpecificationVersion thisVersion = this.platform.getSpecification().getVersion();
            SpecificationVersion otherVersion = other.platform.getSpecification().getVersion();
            return thisVersion.equals(otherVersion);
        }

        @Override
        public String toString() {
            return platform.getDisplayName();
        }
    }

    private static class ProjectPlatformComboItem {
        private final ProjectPlatform platform;

        public ProjectPlatformComboItem(ProjectPlatform platform) {
            ExceptionHelper.checkNotNullArgument(platform, "platform");
            this.platform = platform;
        }

        public ProjectPlatform getPlatform() {
            return platform;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 41 * hash + Objects.hashCode(platform.getName());
            hash = 41 * hash + Objects.hashCode(platform.getVersion());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;

            final ProjectPlatformComboItem other = (ProjectPlatformComboItem)obj;
            return Objects.equals(this.platform.getName(), other.platform.getName())
                    && Objects.equals(this.platform.getVersion(), other.platform.getVersion());
        }

        @Override
        public String toString() {
            return platform.getDisplayName();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The
     * content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSourceLevelCombo = new javax.swing.JComboBox<String>();
        jGradleHomeEdit = new javax.swing.JTextField();
        jGradleHomeInherit = new javax.swing.JCheckBox();
        jSourceEncodingInherit = new javax.swing.JCheckBox();
        jPlatformComboInherit = new javax.swing.JCheckBox();
        jScriptPlatformCombo = new javax.swing.JComboBox<JavaPlatformComboItem>();
        jSourceLevelComboInherit = new javax.swing.JCheckBox();
        jScriptPlatformInherit = new javax.swing.JCheckBox();
        jPlatformPreferenceButton = new javax.swing.JButton();
        jSourceEncoding = new javax.swing.JTextField();
        jPlatformCombo = new javax.swing.JComboBox<ProjectPlatformComboItem>();
        jManageBuiltInTasks = new javax.swing.JButton();
        jGradleHomeCaption = new javax.swing.JLabel();
        jManageTasksButton = new javax.swing.JButton();
        jSourceEncodingCaption = new javax.swing.JLabel();

        jSourceLevelCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1.3", "1.4", "1.5", "1.6", "1.7", "1.8" }));

        jGradleHomeEdit.setText(org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jGradleHomeEdit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jGradleHomeInherit, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jGradleHomeInherit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jSourceEncodingInherit, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jSourceEncodingInherit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jPlatformComboInherit, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jPlatformComboInherit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jSourceLevelComboInherit, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jSourceLevelComboInherit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jScriptPlatformInherit, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jScriptPlatformInherit.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jPlatformPreferenceButton, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jPlatformPreferenceButton.text")); // NOI18N
        jPlatformPreferenceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jPlatformPreferenceButtonActionPerformed(evt);
            }
        });

        jSourceEncoding.setText(org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jSourceEncoding.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jManageBuiltInTasks, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jManageBuiltInTasks.text")); // NOI18N
        jManageBuiltInTasks.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jManageBuiltInTasksActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jGradleHomeCaption, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jGradleHomeCaption.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jManageTasksButton, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jManageTasksButton.text")); // NOI18N
        jManageTasksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jManageTasksButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jSourceEncodingCaption, org.openide.util.NbBundle.getMessage(CommonProjectPropertiesPanel.class, "CommonProjectPropertiesPanel.jSourceEncodingCaption.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jSourceEncodingCaption)
                            .addComponent(jGradleHomeCaption))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScriptPlatformCombo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jGradleHomeEdit)
                            .addComponent(jSourceLevelCombo, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPlatformCombo, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jSourceEncoding, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(6, 6, 6)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jSourceEncodingInherit, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jGradleHomeInherit, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jScriptPlatformInherit, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPlatformComboInherit, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jSourceLevelComboInherit, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jManageTasksButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jManageBuiltInTasks)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 98, Short.MAX_VALUE)
                        .addComponent(jPlatformPreferenceButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSourceEncodingCaption)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jSourceEncoding, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jSourceEncodingInherit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jGradleHomeCaption, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jGradleHomeEdit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jGradleHomeInherit))
                .addGap(34, 34, 34)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jScriptPlatformCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScriptPlatformInherit))
                .addGap(34, 34, 34)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jPlatformCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPlatformComboInherit))
                .addGap(34, 34, 34)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jSourceLevelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jSourceLevelComboInherit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jManageTasksButton)
                    .addComponent(jManageBuiltInTasks)
                    .addComponent(jPlatformPreferenceButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jPlatformPreferenceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jPlatformPreferenceButtonActionPerformed
        if (PlatformPriorityPanel.showDialog(this)) {
            if (currentValues != null) {
                currentValues.refreshPlatformCombos();
            }
        }
    }//GEN-LAST:event_jPlatformPreferenceButtonActionPerformed

    private void jManageBuiltInTasksActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jManageBuiltInTasksActionPerformed
        if (currentSettings != null && currentProfileName != null) {
            displayManageBuiltInTasksPanel(currentProfileName, currentSettings);
        }
    }//GEN-LAST:event_jManageBuiltInTasksActionPerformed

    private void jManageTasksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jManageTasksButtonActionPerformed
        currentSettings.currentProfileSettings().getValue().getKey();

        if (currentSettings != null && currentProfileName != null) {
            displayManageTasksPanel(currentProfileName, currentSettings);
        }
    }//GEN-LAST:event_jManageTasksButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jGradleHomeCaption;
    private javax.swing.JTextField jGradleHomeEdit;
    private javax.swing.JCheckBox jGradleHomeInherit;
    private javax.swing.JButton jManageBuiltInTasks;
    private javax.swing.JButton jManageTasksButton;
    private javax.swing.JComboBox<ProjectPlatformComboItem> jPlatformCombo;
    private javax.swing.JCheckBox jPlatformComboInherit;
    private javax.swing.JButton jPlatformPreferenceButton;
    private javax.swing.JComboBox<JavaPlatformComboItem> jScriptPlatformCombo;
    private javax.swing.JCheckBox jScriptPlatformInherit;
    private javax.swing.JTextField jSourceEncoding;
    private javax.swing.JLabel jSourceEncodingCaption;
    private javax.swing.JCheckBox jSourceEncodingInherit;
    private javax.swing.JComboBox<String> jSourceLevelCombo;
    private javax.swing.JCheckBox jSourceLevelComboInherit;
    // End of variables declaration//GEN-END:variables
}
