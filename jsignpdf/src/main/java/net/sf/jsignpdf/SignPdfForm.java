/*
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is 'JSignPdf, a free application for PDF signing'.
 *
 * The Initial Developer of the Original Code is Josef Cacek.
 * Portions created by Josef Cacek are Copyright (C) Josef Cacek. All Rights Reserved.
 *
 * Contributor(s): Josef Cacek.
 *
 * Alternatively, the contents of this file may be used under the terms
 * of the GNU Lesser General Public License, version 2.1 (the  "LGPL License"), in which case the
 * provisions of LGPL License are applicable instead of those
 * above. If you wish to allow use of your version of this file only
 * under the terms of the LGPL License and not to allow others to use
 * your version of this file under the MPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and
 * other provisions required by the LGPL License. If you do not delete
 * the provisions above, a recipient may use your version of this file
 * under either the MPL or the LGPL License.
 */
package net.sf.jsignpdf;

import java.awt.Dimension;
import static net.sf.jsignpdf.Constants.RES;
import static net.sf.jsignpdf.Constants.LOGGER;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.net.URL;
import java.security.KeyStore;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import net.sf.jsignpdf.types.CertificationLevel;
import net.sf.jsignpdf.types.HashAlgorithm;
import net.sf.jsignpdf.types.PDFEncryption;
import net.sf.jsignpdf.types.PrintRight;
import net.sf.jsignpdf.utils.GuiUtils;
import net.sf.jsignpdf.utils.KeyStoreUtils;
import net.sf.jsignpdf.utils.PKCS11Utils;
import net.sf.jsignpdf.utils.PropertyProvider;

import org.apache.commons.lang3.StringUtils;

/**
 * GUI for PDFSigner.
 *
 * @author Josef Cacek
 */
public class SignPdfForm extends javax.swing.JFrame implements SignResultListener {

    private static final long serialVersionUID = 1L;

    private SignerFileChooser fileChooser = new SignerFileChooser();

    protected final PropertyProvider props = PropertyProvider.getInstance();

    private boolean autoclose = false;
    private BasicSignerOptions options;
    private SignerLogic signerLogic;
    private VisibleSignatureDialog vsDialog;
    private TsaDialog tsaDialog;

    public SignPdfForm(int aCloseOperation) {
        this(aCloseOperation, null);
    }

    public SignPdfForm(int aCloseOperation, BasicSignerOptions options) {
        initComponents();
        
        chkbVisibleSig.setVisible(false);

        if (options == null) {
            options = new BasicSignerOptions();
        }

        this.options = options;

        signerLogic = new SignerLogic(options);
        vsDialog = new VisibleSignatureDialog(this, true, options, fileChooser);
        tsaDialog = new TsaDialog(this, true, options);

        options.loadOptions();
        try {
            options.loadCmdLine();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.fine(e.getMessage());
        }
        translateLabels();

        setDefaultCloseOperation(aCloseOperation);
        getRootPane().setDefaultButton(btnSignIt);

        // set Icon of frames
        URL tmpImgUrl = getClass().getResource("/net/sf/jsignpdf/signedpdf32.png");
        setIconImage(Toolkit.getDefaultToolkit().getImage(tmpImgUrl));
        infoDialog.setIconImage(getIconImage());
        infoDialog.getRootPane().setDefaultButton(btnInfoClose);
        infoDialog.pack();
        GuiUtils.center(infoDialog);

        // setIconImage is available from Java 1.6!
        // rightsDialog.setIconImage(getIconImage());
        rightsDialog.getRootPane().setDefaultButton(btnRightsOK);
        rightsDialog.pack();
        GuiUtils.center(rightsDialog);

        tsaDialog.pack();
        GuiUtils.center(tsaDialog);

        vsDialog.pack();
        GuiUtils.center(vsDialog);

        options.setListener(this);

        final Set<String> tmpKsTypes = KeyStoreUtils.getKeyStores();
        cbKeystoreType.setModel(new DefaultComboBoxModel(tmpKsTypes.toArray(new String[tmpKsTypes.size()])));
        if (tmpKsTypes.contains(Constants.KEYSTORE_TYPE_WINDOWS_MY)) {
            cbKeystoreType.setSelectedItem(Constants.KEYSTORE_TYPE_WINDOWS_MY);
        }

        cbPdfEncryption.setModel(new DefaultComboBoxModel(PDFEncryption.values()));
        cbCertLevel.setModel(new DefaultComboBoxModel(CertificationLevel.values()));
        cbHashAlgorithm.setModel(new DefaultComboBoxModel(HashAlgorithm.values()));
        cbPrinting.setModel(new DefaultComboBoxModel(PrintRight.values()));

        JTextAreaHandler textHandler = new JTextAreaHandler(infoTextArea);
        textHandler.setLevel(Level.ALL);
        LOGGER.addHandler(textHandler);
        
        updateFromOptions();

        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferHandler.TransferSupport info) {
                return info.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferHandler.TransferSupport info) {
                if (!info.isDrop() || !info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }

                List<File> data;
                try {
                    data = (List<File>) info.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    return false;
                }

                String lastFile = "";
                for (File file : data) {
                    lastFile = file.getPath();
                }
                tfInPdfFile.setText(lastFile);

                return true;
            }
        };

        tfInPdfFile.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fillOutputPdfName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fillOutputPdfName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fillOutputPdfName();
            }
        });
        tfInPdfFile.setTransferHandler(handler);
        this.setTransferHandler(handler);
    }

    /**
     * Application translations.
     */
    private void translateLabels() {
        setTitle(RES.get("gui.title", new String[] { Constants.VERSION }));
        setLabelAndMnemonic(lblKeystoreType, "gui.keystoreType.label");
        setLabelAndMnemonic(chkbAdvanced, "gui.advancedView.checkbox");
        setLabelAndMnemonic(lblKeystoreFile, "gui.keystoreFile.label");
        setLabelAndMnemonic(lblKeystorePwd, "gui.keystorePassword.label");
        setLabelAndMnemonic(chkbStorePwd, "gui.storePasswords.checkbox");
        setLabelAndMnemonic(lblAlias, "gui.alias.label");
        setLabelAndMnemonic(btnLoadAliases, "gui.loadAliases.button");
        setLabelAndMnemonic(lblKeyPwd, "gui.keyPassword.label");
        setLabelAndMnemonic(lblInPdfFile, "gui.inPdfFile.label");
        setLabelAndMnemonic(btnRights, "gui.rights.button");
        setLabelAndMnemonic(lblPdfEncryption, "gui.pdfEncryption.label");
        setLabelAndMnemonic(lblPdfOwnerPwd, "gui.pdfOwnerPwd.label");
        setLabelAndMnemonic(lblPdfUserPwd, "gui.pdfUserPwd.label");
        setLabelAndMnemonic(lblEncCertFile, "gui.encryptionCertFile.label");
        setLabelAndMnemonic(lblOutPdfFile, "gui.outPdfFile.label");
        setLabelAndMnemonic(lblReason, "gui.reason.label");
        setLabelAndMnemonic(lblLocation, "gui.location.label");
        setLabelAndMnemonic(lblContact, "gui.contact.label");
        setLabelAndMnemonic(lblCertLevel, "gui.certLevel.label");
        setLabelAndMnemonic(chkbAppendSignature, "gui.appendSignature.checkbox");
        setLabelAndMnemonic(lblHashAlgorithm, "gui.hashAlgorithm.label");

        btnKeystoreFile.setText(RES.get("gui.browse.button"));
        btnEncCertFile.setText(RES.get("gui.browse.button"));
        btnInPdfFile.setText(RES.get("gui.browse.button"));
        btnOutPdfFile.setText(RES.get("gui.browse.button"));

        setLabelAndMnemonic(btnSignIt, "gui.signIt.button");

        infoDialog.setTitle(RES.get("gui.info.title"));
        btnInfoClose.setText(RES.get("gui.info.close.button"));

        rightsDialog.setTitle(RES.get("gui.rights.title"));
        setLabelAndMnemonic(lblPrinting, "gui.rights.printing.label");
        setLabelAndMnemonic(lblRights, "gui.rights.rights.label");
        setLabelAndMnemonic(chkbAllowCopy, "gui.rights.copy.checkbox");
        setLabelAndMnemonic(chkbAllowAssembly, "gui.rights.assembly.checkbox");
        setLabelAndMnemonic(chkbAllowFillIn, "gui.rights.fillIn.checkbox");
        setLabelAndMnemonic(chkbAllowScreenReaders, "gui.rights.screenReaders.checkbox");
        setLabelAndMnemonic(chkbAllowModifyAnnotations, "gui.rights.modifyAnnotations.checkbox");
        setLabelAndMnemonic(chkbAllowModifyContent, "gui.rights.modifyContents.checkbox");

        setLabelAndMnemonic(chkbVisibleSig, "gui.visibleSignature.checkbox");
        setLabelAndMnemonic(btnVisibleSigSettings, "gui.visibleSignatureSettings.button");

        setLabelAndMnemonic(btnTsaOcsp, "gui.tsaOcsp.button");
    }

    /**
     * Sets translations and mnemonics for labels and different kind of buttons
     *
     * @param aComponent component in which should be label set
     * @param aKey message key
     */
    private void setLabelAndMnemonic(final JComponent aComponent, final String aKey) {
        RES.setLabelAndMnemonic(aComponent, aKey);
    }

    /**
     * Loads properties saved by previous run of application
     */
    private void updateFromOptions() {
        if (StringUtils.isNotEmpty(options.getKsType())) {
            cbKeystoreType.setSelectedItem(options.getKsType());
        } else if (cbKeystoreType.getSelectedItem() == null) {
            cbKeystoreType.setSelectedItem(KeyStore.getDefaultType());
        }
        chkbAdvanced.setSelected(options.isAdvanced());
        tfKeystoreFile.setText(options.getKsFile());
        pfKeystorePwd.setText(options.getKsPasswdStr());
        chkbStorePwd.setSelected(options.isStorePasswords());
        cbAlias.setSelectedItem(options.getKeyAlias());
        pfKeyPwd.setText(options.getKeyPasswdStr());
        tfInPdfFile.setText(options.getInFile());
        cbPdfEncryption.setSelectedItem(options.getPdfEncryption());
        pfPdfOwnerPwd.setText(options.getPdfOwnerPwdStr());
        pfPdfUserPwd.setText(options.getPdfUserPwdStr());
        tfEncCertFile.setText(options.getPdfEncryptionCertFile());
        tfOutPdfFile.setText(options.getOutFile());
        tfReason.setText(options.getReason());
        tfLocation.setText(options.getLocation());
        tfContact.setText(options.getContact());
        cbCertLevel.setSelectedItem(options.getCertLevel());
        cbHashAlgorithm.setSelectedItem(options.getHashAlgorithm());
        chkbAppendSignature.setSelected(options.isAppend());

        cbPrinting.setSelectedItem(options.getRightPrinting());
        chkbAllowCopy.setSelected(options.isRightCopy());
        chkbAllowAssembly.setSelected(options.isRightAssembly());
        chkbAllowFillIn.setSelected(options.isRightFillIn());
        chkbAllowScreenReaders.setSelected(options.isRightScreanReaders());
        chkbAllowModifyAnnotations.setSelected(options.isRightModifyAnnotations());
        chkbAllowModifyContent.setSelected(options.isRightModifyContents());

        chkbVisibleSig.setSelected(options.isVisible());

        refreshView();
        pack();
    }

    /**
     * stores values from this Form to the instance of {@link SignerOptions}
     */
    private void storeToOptions() {
        options.setKsType((String) cbKeystoreType.getSelectedItem());
        options.setAdvanced(chkbAdvanced.isSelected());
        options.setKsFile(tfKeystoreFile.getText());
        options.setKsPasswd(pfKeystorePwd.getPassword());
        options.setStorePasswords(chkbStorePwd.isSelected());
        if (cbAlias.getSelectedItem() != options.getKeyAlias() || cbAlias.getSelectedIndex() > -1) {
            options.setKeyAlias((String) cbAlias.getSelectedItem());
            options.setKeyIndex(cbAlias.getSelectedIndex());
        }
        options.setKeyPasswd(pfKeyPwd.getPassword());
        options.setInFile(tfInPdfFile.getText());
        options.setPdfEncryption((PDFEncryption) cbPdfEncryption.getSelectedItem());
        options.setPdfOwnerPwd(pfPdfOwnerPwd.getPassword());
        options.setPdfUserPwd(pfPdfUserPwd.getPassword());
        options.setPdfEncryptionCertFile(tfEncCertFile.getText());
        options.setOutFile(tfOutPdfFile.getText());
        options.setReason(tfReason.getText());
        options.setLocation(tfLocation.getText());
        options.setContact(tfContact.getText());
        options.setCertLevel((CertificationLevel) cbCertLevel.getSelectedItem());
        options.setHashAlgorithm((HashAlgorithm) cbHashAlgorithm.getSelectedItem());
        options.setAppend(chkbAppendSignature.isSelected());

        options.setRightPrinting((PrintRight) cbPrinting.getSelectedItem());
        options.setRightCopy(chkbAllowCopy.isSelected());
        options.setRightAssembly(chkbAllowAssembly.isSelected());
        options.setRightFillIn(chkbAllowFillIn.isSelected());
        options.setRightScreanReaders(chkbAllowScreenReaders.isSelected());
        options.setRightModifyAnnotations(chkbAllowModifyAnnotations.isSelected());
        options.setRightModifyContents(chkbAllowModifyContent.isSelected());

        options.setVisible(chkbVisibleSig.isSelected());
    }

    /**
     * Handles visibility of components depending on options settings .
     */
    private void refreshView() {
        boolean advanced = chkbAdvanced.isSelected();
        //boolean visibleSignature = chkbVisibleSig.isSelected();
        boolean visibleSignature = true;
        boolean keystoreFileVisible = true;
        boolean keystorePwdVisible = true;
        boolean keyPwdVisible = advanced;
        boolean storePwdVisible = true;
        boolean encryptionEnabled = chkbAdvanced.isSelected() && (cbPdfEncryption.getSelectedItem() != PDFEncryption.NONE);
        boolean passwordEncryptionVisible = encryptionEnabled && (cbPdfEncryption.getSelectedItem() == PDFEncryption.PASSWORD);
        boolean certEncryptionVisible = encryptionEnabled && (cbPdfEncryption.getSelectedItem() == PDFEncryption.CERTIFICATE);

        final String selectedType = (String) cbKeystoreType.getSelectedItem();
        if (StringUtils.equals(Constants.KEYSTORE_TYPE_WINDOWS_MY, selectedType)) {
            keystoreFileVisible = false;
            keystorePwdVisible = false;
            keyPwdVisible = false;
            storePwdVisible = advanced;
        }

        btnLoadAliases.setVisible(advanced);
        lblAlias.setVisible(advanced);
        cbAlias.setVisible(advanced);
        btnInfokeyAlias.setVisible(advanced);

        lblPdfEncryption.setVisible(advanced);
        cbPdfEncryption.setVisible(advanced);
        btnInfoPdfEncryption.setVisible(advanced);

        lblCertLevel.setVisible(advanced);
        cbCertLevel.setVisible(advanced);
        btnInfoCertificationLevel.setVisible(advanced);

        lblHashAlgorithm.setVisible(advanced);
        cbHashAlgorithm.setVisible(advanced);

        chkbAppendSignature.setEnabled(!encryptionEnabled);
        chkbAppendSignature.setVisible(advanced);
        if (encryptionEnabled) {
            chkbAppendSignature.setSelected(false);
        }

        btnTsaOcsp.setVisible(advanced);

        lblKeystoreFile.setVisible(keystoreFileVisible);
        tfKeystoreFile.setVisible(keystoreFileVisible);
        btnKeystoreFile.setVisible(keystoreFileVisible);

        lblKeystorePwd.setVisible(keystorePwdVisible);
        pfKeystorePwd.setVisible(keystorePwdVisible);

        lblKeyPwd.setVisible(keyPwdVisible);
        pfKeyPwd.setVisible(keyPwdVisible);

        lblPdfOwnerPwd.setVisible(passwordEncryptionVisible);
        pfPdfOwnerPwd.setVisible(passwordEncryptionVisible);

        lblPdfUserPwd.setVisible(passwordEncryptionVisible);
        pfPdfUserPwd.setVisible(passwordEncryptionVisible);

        lblEncCertFile.setVisible(certEncryptionVisible);
        tfEncCertFile.setVisible(certEncryptionVisible);
        btnEncCertFile.setVisible(certEncryptionVisible);

        btnRights.setVisible(encryptionEnabled);

        chkbStorePwd.setVisible(storePwdVisible);

        btnVisibleSigSettings.setEnabled(visibleSignature);
    }

    /**
     * Displays file chooser dialog of given type and with givet FileFilter.
     *
     * @param aFileField assigned textfield
     * @param aFilter filefilter
     * @param aType dialog type (SAVE_DIALOG, OPEN_DIALOG)
     */
    void showFileChooser(final JTextField aFileField, final FileFilter aFilter, final int aType) {
        fileChooser.showFileChooser(aFileField, aFilter, aType);
    }

    /**
     * Suggest output file name.
     */
    private void fillOutputPdfName() {
        String oldName = tfInPdfFile.getText();
        File f = new File(oldName);
        try {
            if (f.exists() && f.isFile()) {

                String justName = f.getName();
                int dotPosition = justName.lastIndexOf('.');
                if (dotPosition > 0) {
                    justName = justName.substring(0, dotPosition);
                }

                File signedPDF = new File(f.getParentFile(), justName + "_signed.pdf");

                tfOutPdfFile.setText(signedPDF.getPath());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            LOGGER.fine(ex.getMessage());
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content
     * of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        infoDialog = new javax.swing.JFrame();
        infoScrollPane = new javax.swing.JScrollPane();
        infoTextArea = new javax.swing.JTextArea();
        btnInfoClose = new javax.swing.JButton();
        rightsDialog = new javax.swing.JDialog();
        lblPrinting = new javax.swing.JLabel();
        cbPrinting = new javax.swing.JComboBox();
        lblRights = new javax.swing.JLabel();
        chkbAllowCopy = new javax.swing.JCheckBox();
        chkbAllowAssembly = new javax.swing.JCheckBox();
        chkbAllowFillIn = new javax.swing.JCheckBox();
        chkbAllowScreenReaders = new javax.swing.JCheckBox();
        chkbAllowModifyAnnotations = new javax.swing.JCheckBox();
        chkbAllowModifyContent = new javax.swing.JCheckBox();
        btnRightsOK = new javax.swing.JButton();
        lblKeystoreType = new javax.swing.JLabel();
        cbKeystoreType = new javax.swing.JComboBox();
        lblKeystoreFile = new javax.swing.JLabel();
        btnVisibleSigSettings = new javax.swing.JButton();
        tfKeystoreFile = new javax.swing.JTextField();
        btnKeystoreFile = new javax.swing.JButton();
        lblKeystorePwd = new javax.swing.JLabel();
        pfKeystorePwd = new javax.swing.JPasswordField();
        chkbStorePwd = new javax.swing.JCheckBox();
        lblAlias = new javax.swing.JLabel();
        cbAlias = new javax.swing.JComboBox();
        btnLoadAliases = new javax.swing.JButton();
        lblKeyPwd = new javax.swing.JLabel();
        pfKeyPwd = new javax.swing.JPasswordField();
        lblInPdfFile = new javax.swing.JLabel();
        tfInPdfFile = new javax.swing.JTextField();
        btnInPdfFile = new javax.swing.JButton();
        lblPdfOwnerPwd = new javax.swing.JLabel();
        pfPdfOwnerPwd = new javax.swing.JPasswordField();
        lblPdfUserPwd = new javax.swing.JLabel();
        pfPdfUserPwd = new javax.swing.JPasswordField();
        lblOutPdfFile = new javax.swing.JLabel();
        tfOutPdfFile = new javax.swing.JTextField();
        btnOutPdfFile = new javax.swing.JButton();
        lblReason = new javax.swing.JLabel();
        tfReason = new javax.swing.JTextField();
        lblLocation = new javax.swing.JLabel();
        tfLocation = new javax.swing.JTextField();
        lblCertLevel = new javax.swing.JLabel();
        cbCertLevel = new javax.swing.JComboBox();
        chkbAppendSignature = new javax.swing.JCheckBox();
        btnSignIt = new javax.swing.JButton();
        chkbAdvanced = new javax.swing.JCheckBox();
        btnRights = new javax.swing.JButton();
        chkbVisibleSig = new javax.swing.JCheckBox();
        lblContact = new javax.swing.JLabel();
        tfContact = new javax.swing.JTextField();
        btnTsaOcsp = new javax.swing.JButton();
        cbHashAlgorithm = new javax.swing.JComboBox();
        lblHashAlgorithm = new javax.swing.JLabel();
        lblPdfEncryption = new javax.swing.JLabel();
        cbPdfEncryption = new javax.swing.JComboBox();
        lblEncCertFile = new javax.swing.JLabel();
        tfEncCertFile = new javax.swing.JTextField();
        btnEncCertFile = new javax.swing.JButton();
        btnInfoOutPdfFile = new javax.swing.JButton();
        btnInfoPdfEncryption = new javax.swing.JButton();
        btnInfoCertificationLevel = new javax.swing.JButton();
        btnInfokeyAlias = new javax.swing.JButton();
        btnInfoKeystoreType = new javax.swing.JButton();

        infoDialog.setTitle("PDF Signer Output");
        infoDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                infoDialogWindowClosing(evt);
            }
        });

        infoTextArea.setEditable(false);
        infoTextArea.setColumns(80);
        infoTextArea.setFont(new java.awt.Font("Courier New", 1, 10)); // NOI18N
        infoTextArea.setRows(25);
        infoTextArea.setMinimumSize(new java.awt.Dimension(200, 180));
        infoScrollPane.setViewportView(infoTextArea);

        infoDialog.getContentPane().add(infoScrollPane, java.awt.BorderLayout.CENTER);

        btnInfoClose.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/back16.png"))); // NOI18N
        btnInfoClose.setText("Close");
        btnInfoClose.setMinimumSize(new java.awt.Dimension(50, 20));
        btnInfoClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoCloseActionPerformed(evt);
            }
        });
        infoDialog.getContentPane().add(btnInfoClose, java.awt.BorderLayout.SOUTH);

        rightsDialog.setModal(true);
        rightsDialog.getContentPane().setLayout(new java.awt.GridBagLayout());

        lblPrinting.setText("Printing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(lblPrinting, gridBagConstraints);

        cbPrinting.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Allow", "Allow Degraded", "Disallow" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(cbPrinting, gridBagConstraints);

        lblRights.setText("Rights");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(lblRights, gridBagConstraints);

        chkbAllowCopy.setText("Copy");
        chkbAllowCopy.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowCopy, gridBagConstraints);

        chkbAllowAssembly.setText("Assembly");
        chkbAllowAssembly.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowAssembly, gridBagConstraints);

        chkbAllowFillIn.setText("Fill In");
        chkbAllowFillIn.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowFillIn, gridBagConstraints);

        chkbAllowScreenReaders.setText("Screen readers");
        chkbAllowScreenReaders.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowScreenReaders, gridBagConstraints);

        chkbAllowModifyAnnotations.setText("Modify annotations");
        chkbAllowModifyAnnotations.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowModifyAnnotations, gridBagConstraints);

        chkbAllowModifyContent.setText("Modify contents");
        chkbAllowModifyContent.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 5);
        rightsDialog.getContentPane().add(chkbAllowModifyContent, gridBagConstraints);

        btnRightsOK.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/back16.png"))); // NOI18N
        btnRightsOK.setText("OK");
        btnRightsOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRightsOKActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(7, 5, 2, 5);
        rightsDialog.getContentPane().add(btnRightsOK, gridBagConstraints);

        setTitle("SignPdf");
        setMaximumSize(new java.awt.Dimension(900, 879));
        setMinimumSize(new java.awt.Dimension(900, 879));
        setPreferredSize(new java.awt.Dimension(700, 749));
        setResizable(false);
        setSize(new java.awt.Dimension(700, 749));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        lblKeystoreType.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblKeystoreType.setLabelFor(cbKeystoreType);
        lblKeystoreType.setText("Keystore type");
        getContentPane().add(lblKeystoreType, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 90, -1, -1));

        cbKeystoreType.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        cbKeystoreType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "PKCS#12", "JKS" }));
        cbKeystoreType.setMinimumSize(new java.awt.Dimension(150, 20));
        cbKeystoreType.setPreferredSize(new java.awt.Dimension(150, 20));
        cbKeystoreType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbKeystoreTypeActionPerformed(evt);
            }
        });
        getContentPane().add(cbKeystoreType, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 90, 474, -1));

        lblKeystoreFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblKeystoreFile.setLabelFor(tfKeystoreFile);
        lblKeystoreFile.setText("Keystore file");
        getContentPane().add(lblKeystoreFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 120, -1, 37));

        btnVisibleSigSettings.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnVisibleSigSettings.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/options16.png"))); // NOI18N
        btnVisibleSigSettings.setText("Settings");
        btnVisibleSigSettings.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnVisibleSigSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnVisibleSigSettingsActionPerformed(evt);
            }
        });
        getContentPane().add(btnVisibleSigSettings, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 760, 151, -1));

        tfKeystoreFile.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfKeystoreFile.setMinimumSize(new java.awt.Dimension(250, 20));
        tfKeystoreFile.setPreferredSize(new java.awt.Dimension(250, 20));
        getContentPane().add(tfKeystoreFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 130, 474, -1));

        btnKeystoreFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnKeystoreFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/fileopen16.png"))); // NOI18N
        btnKeystoreFile.setText("Browse...");
        btnKeystoreFile.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnKeystoreFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnKeystoreFileActionPerformed(evt);
            }
        });
        getContentPane().add(btnKeystoreFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 130, 151, -1));

        lblKeystorePwd.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblKeystorePwd.setLabelFor(pfKeystorePwd);
        lblKeystorePwd.setText("Keystore password");
        getContentPane().add(lblKeystorePwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 160, -1, 37));

        pfKeystorePwd.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        pfKeystorePwd.setMinimumSize(new java.awt.Dimension(150, 20));
        pfKeystorePwd.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(pfKeystorePwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 170, 474, -1));

        chkbStorePwd.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        chkbStorePwd.setText("Store passwords");
        chkbStorePwd.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        getContentPane().add(chkbStorePwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 170, 165, -1));

        lblAlias.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblAlias.setLabelFor(cbAlias);
        lblAlias.setText("Key alias");
        getContentPane().add(lblAlias, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 200, -1, 37));

        cbAlias.setEditable(true);
        cbAlias.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        cbAlias.setMinimumSize(new java.awt.Dimension(150, 20));
        cbAlias.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(cbAlias, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 210, 474, -1));

        btnLoadAliases.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnLoadAliases.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/refresh16.png"))); // NOI18N
        btnLoadAliases.setText("Load keys");
        btnLoadAliases.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnLoadAliases.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLoadAliasesActionPerformed(evt);
            }
        });
        getContentPane().add(btnLoadAliases, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 210, 151, -1));

        lblKeyPwd.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblKeyPwd.setLabelFor(pfKeyPwd);
        lblKeyPwd.setText("Key password");
        getContentPane().add(lblKeyPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 250, -1, 37));

        pfKeyPwd.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        pfKeyPwd.setMinimumSize(new java.awt.Dimension(150, 20));
        pfKeyPwd.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(pfKeyPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 250, 474, -1));

        lblInPdfFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblInPdfFile.setLabelFor(tfInPdfFile);
        lblInPdfFile.setText("Input PDF file");
        getContentPane().add(lblInPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 290, -1, 37));

        tfInPdfFile.setEditable(false);
        tfInPdfFile.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfInPdfFile.setMinimumSize(new java.awt.Dimension(150, 20));
        tfInPdfFile.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfInPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 290, 474, -1));

        btnInPdfFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnInPdfFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/fileopen16.png"))); // NOI18N
        btnInPdfFile.setText("Browse...");
        btnInPdfFile.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnInPdfFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInPdfFileActionPerformed(evt);
            }
        });
        getContentPane().add(btnInPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 290, 151, -1));

        lblPdfOwnerPwd.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblPdfOwnerPwd.setLabelFor(pfPdfOwnerPwd);
        lblPdfOwnerPwd.setText("Owner password");
        getContentPane().add(lblPdfOwnerPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 410, -1, 37));

        pfPdfOwnerPwd.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        pfPdfOwnerPwd.setMinimumSize(new java.awt.Dimension(150, 20));
        pfPdfOwnerPwd.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(pfPdfOwnerPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 420, 474, -1));

        lblPdfUserPwd.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblPdfUserPwd.setLabelFor(pfPdfUserPwd);
        lblPdfUserPwd.setText("User password");
        getContentPane().add(lblPdfUserPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 450, -1, 37));

        pfPdfUserPwd.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        pfPdfUserPwd.setMinimumSize(new java.awt.Dimension(150, 20));
        pfPdfUserPwd.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(pfPdfUserPwd, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 460, 474, -1));

        lblOutPdfFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblOutPdfFile.setLabelFor(tfOutPdfFile);
        lblOutPdfFile.setText("Output PDF file");
        getContentPane().add(lblOutPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 490, -1, 37));

        tfOutPdfFile.setEditable(false);
        tfOutPdfFile.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfOutPdfFile.setMinimumSize(new java.awt.Dimension(150, 20));
        tfOutPdfFile.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfOutPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 500, 474, -1));

        btnOutPdfFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnOutPdfFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/fileopen16.png"))); // NOI18N
        btnOutPdfFile.setText("Browse...");
        btnOutPdfFile.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnOutPdfFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOutPdfFileActionPerformed(evt);
            }
        });
        getContentPane().add(btnOutPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 500, 151, -1));

        lblReason.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblReason.setLabelFor(tfReason);
        lblReason.setText("Reason");
        getContentPane().add(lblReason, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 550, -1, 37));

        tfReason.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfReason.setMinimumSize(new java.awt.Dimension(150, 20));
        tfReason.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfReason, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 560, 474, -1));

        lblLocation.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblLocation.setLabelFor(tfLocation);
        lblLocation.setText("Location");
        getContentPane().add(lblLocation, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 590, -1, 37));

        tfLocation.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfLocation.setMinimumSize(new java.awt.Dimension(150, 20));
        tfLocation.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfLocation, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 600, 474, -1));

        lblCertLevel.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblCertLevel.setLabelFor(cbCertLevel);
        lblCertLevel.setText("Certification level");
        getContentPane().add(lblCertLevel, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 680, -1, 37));

        cbCertLevel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        cbCertLevel.setMinimumSize(new java.awt.Dimension(150, 20));
        cbCertLevel.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(cbCertLevel, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 680, 474, -1));

        chkbAppendSignature.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        chkbAppendSignature.setText("Append signature");
        chkbAppendSignature.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        getContentPane().add(chkbAppendSignature, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 530, 490, -1));

        btnSignIt.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnSignIt.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/signedpdf26.png"))); // NOI18N
        btnSignIt.setText("Sign It");
        btnSignIt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSignItActionPerformed(evt);
            }
        });
        getContentPane().add(btnSignIt, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 790, 151, -1));

        chkbAdvanced.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        chkbAdvanced.setSelected(true);
        chkbAdvanced.setText("Advanced view");
        chkbAdvanced.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkbAdvanced.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkbAdvancedActionPerformed(evt);
            }
        });
        getContentPane().add(chkbAdvanced, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 90, 165, -1));

        btnRights.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnRights.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/security16.png"))); // NOI18N
        btnRights.setText("Rights");
        btnRights.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnRights.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRightsActionPerformed(evt);
            }
        });
        getContentPane().add(btnRights, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 330, 151, -1));

        chkbVisibleSig.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        chkbVisibleSig.setText("Visible signature");
        chkbVisibleSig.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkbVisibleSig.setEnabled(false);
        chkbVisibleSig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkbVisibleSigActionPerformed(evt);
            }
        });
        getContentPane().add(chkbVisibleSig, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 760, 490, -1));

        lblContact.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblContact.setLabelFor(tfContact);
        lblContact.setText("Contact");
        getContentPane().add(lblContact, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 640, -1, 37));

        tfContact.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfContact.setMinimumSize(new java.awt.Dimension(150, 20));
        tfContact.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfContact, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 640, 474, -1));

        btnTsaOcsp.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnTsaOcsp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/clock16.png"))); // NOI18N
        btnTsaOcsp.setText("TSA & OCSP");
        btnTsaOcsp.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnTsaOcsp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTsaOcspActionPerformed(evt);
            }
        });
        getContentPane().add(btnTsaOcsp, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 640, 151, -1));

        cbHashAlgorithm.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        cbHashAlgorithm.setMinimumSize(new java.awt.Dimension(150, 20));
        cbHashAlgorithm.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(cbHashAlgorithm, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 730, 474, -1));

        lblHashAlgorithm.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblHashAlgorithm.setLabelFor(cbHashAlgorithm);
        lblHashAlgorithm.setText("Hash algorithm");
        getContentPane().add(lblHashAlgorithm, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 720, -1, 37));

        lblPdfEncryption.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblPdfEncryption.setLabelFor(cbPdfEncryption);
        lblPdfEncryption.setText("PDF Encryption");
        getContentPane().add(lblPdfEncryption, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 330, -1, 37));

        cbPdfEncryption.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        cbPdfEncryption.setMinimumSize(new java.awt.Dimension(150, 20));
        cbPdfEncryption.setPreferredSize(new java.awt.Dimension(150, 20));
        cbPdfEncryption.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbPdfEncryptionActionPerformed(evt);
            }
        });
        getContentPane().add(cbPdfEncryption, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 340, 474, -1));

        lblEncCertFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        lblEncCertFile.setLabelFor(tfEncCertFile);
        lblEncCertFile.setText("Certificate file");
        getContentPane().add(lblEncCertFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 370, -1, 37));

        tfEncCertFile.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tfEncCertFile.setMinimumSize(new java.awt.Dimension(150, 20));
        tfEncCertFile.setPreferredSize(new java.awt.Dimension(150, 20));
        getContentPane().add(tfEncCertFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(230, 380, 474, -1));

        btnEncCertFile.setFont(new java.awt.Font("Segoe UI Emoji", 1, 14)); // NOI18N
        btnEncCertFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/fileopen16.png"))); // NOI18N
        btnEncCertFile.setText("Browse...");
        btnEncCertFile.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        btnEncCertFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEncCertFileActionPerformed(evt);
            }
        });
        getContentPane().add(btnEncCertFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 370, 151, -1));

        btnInfoOutPdfFile.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnInfoOutPdfFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/icon-info.png"))); // NOI18N
        btnInfoOutPdfFile.setBorder(null);
        btnInfoOutPdfFile.setContentAreaFilled(false);
        btnInfoOutPdfFile.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnInfoOutPdfFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoOutPdfFileActionPerformed(evt);
            }
        });
        getContentPane().add(btnInfoOutPdfFile, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 500, -1, -1));

        btnInfoPdfEncryption.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnInfoPdfEncryption.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/icon-info.png"))); // NOI18N
        btnInfoPdfEncryption.setBorder(null);
        btnInfoPdfEncryption.setContentAreaFilled(false);
        btnInfoPdfEncryption.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnInfoPdfEncryption.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoPdfEncryptionActionPerformed(evt);
            }
        });
        getContentPane().add(btnInfoPdfEncryption, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 340, -1, -1));

        btnInfoCertificationLevel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnInfoCertificationLevel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/icon-info.png"))); // NOI18N
        btnInfoCertificationLevel.setBorder(null);
        btnInfoCertificationLevel.setContentAreaFilled(false);
        btnInfoCertificationLevel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnInfoCertificationLevel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoCertificationLevelActionPerformed(evt);
            }
        });
        getContentPane().add(btnInfoCertificationLevel, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 730, -1, -1));

        btnInfokeyAlias.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnInfokeyAlias.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/icon-info.png"))); // NOI18N
        btnInfokeyAlias.setBorder(null);
        btnInfokeyAlias.setContentAreaFilled(false);
        btnInfokeyAlias.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnInfokeyAlias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfokeyAliasActionPerformed(evt);
            }
        });
        getContentPane().add(btnInfokeyAlias, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 210, -1, -1));

        btnInfoKeystoreType.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnInfoKeystoreType.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jsignpdf/icon-info.png"))); // NOI18N
        btnInfoKeystoreType.setBorder(null);
        btnInfoKeystoreType.setContentAreaFilled(false);
        btnInfoKeystoreType.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnInfoKeystoreType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInfoKeystoreTypeActionPerformed(evt);
            }
        });
        getContentPane().add(btnInfoKeystoreType, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 90, -1, -1));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnInfoKeystoreTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfoKeystoreTypeActionPerformed
        JOptionPane.showMessageDialog(this, "Es una colección de certificados y claves. Por defecto Microsoft te generará una, sino seleccione otra opción");
    }//GEN-LAST:event_btnInfoKeystoreTypeActionPerformed

    private void btnInfokeyAliasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfokeyAliasActionPerformed
        JOptionPane.showMessageDialog(this, "Seleccione una llave o certicado ya cargado. Por defecto Microsoft generará una");
    }//GEN-LAST:event_btnInfokeyAliasActionPerformed

    private void btnInfoPdfEncryptionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfoPdfEncryptionActionPerformed
        JOptionPane.showMessageDialog(this, "Opción para proteger el documento y solo los usuarios autorizados puedan verlo");
    }//GEN-LAST:event_btnInfoPdfEncryptionActionPerformed

    private void btnInfoOutPdfFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfoOutPdfFileActionPerformed
        JOptionPane.showMessageDialog(this, "La ubicación donde se guardará el documento firmado");
    }//GEN-LAST:event_btnInfoOutPdfFileActionPerformed

    private void btnInfoCertificationLevelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInfoCertificationLevelActionPerformed
        JOptionPane.showMessageDialog(this, "Se cifra el documento con un algoritmo donde solo los usuarios autorizados podrán cifrarlos");
    }//GEN-LAST:event_btnInfoCertificationLevelActionPerformed

    private void btnEncCertFileActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnEncCertFileActionPerformed
        showFileChooser(tfEncCertFile, null, JFileChooser.OPEN_DIALOG);
    }// GEN-LAST:event_btnEncCertFileActionPerformed

    private void cbPdfEncryptionActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbPdfEncryptionActionPerformed
        refreshView();
        pack();
    }// GEN-LAST:event_cbPdfEncryptionActionPerformed

    private void btnVisibleSigSettingsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnVisibleSigSettingsActionPerformed
        storeToOptions();
        vsDialog.setVisible(true);
    }// GEN-LAST:event_btnVisibleSigSettingsActionPerformed

    private void chkbVisibleSigActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_chkbVisibleSigActionPerformed
        refreshView();
    }// GEN-LAST:event_chkbVisibleSigActionPerformed

    private void btnRightsOKActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnRightsOKActionPerformed
        rightsDialog.setVisible(false);
    }// GEN-LAST:event_btnRightsOKActionPerformed

    private void btnRightsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnRightsActionPerformed
        rightsDialog.setVisible(true);
    }// GEN-LAST:event_btnRightsActionPerformed

    private void btnLoadAliasesActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnLoadAliasesActionPerformed
        storeToOptions();
        try {
            cbAlias.setModel(new DefaultComboBoxModel(KeyStoreUtils.getKeyAliases(options)));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }// GEN-LAST:event_btnLoadAliasesActionPerformed

    private void chkbAdvancedActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_chkbAdvancedActionPerformed
        refreshView();
        pack();
    }// GEN-LAST:event_chkbAdvancedActionPerformed

    private void infoDialogWindowClosing(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_infoDialogWindowClosing
        if (btnInfoClose.isEnabled()) {
            setVisible(true);
        }
    }// GEN-LAST:event_infoDialogWindowClosing

    /**
     * @see net.sf.jsignpdf.SignResultListener#signerFinishedEvent(Exception)
     */
    @Override
    public synchronized void signerFinishedEvent(Throwable e) {
        if (e instanceof SSLHandshakeException) {
            JOptionPane.showMessageDialog(this, RES.get("error.sslHandshakeException"), "Error", JOptionPane.WARNING_MESSAGE);
        }
        btnInfoClose.setEnabled(true);
        infoDialog.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
        if (!btnInfoClose.hasFocus())
            btnInfoClose.requestFocus();
        if (autoclose)
            btnInfoCloseActionPerformed(null);
    }

    private void btnInfoCloseActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnInfoCloseActionPerformed
        infoDialog.setVisible(false);
        setVisible(true);
    }// GEN-LAST:event_btnInfoCloseActionPerformed

    /**
     * Checks if file exists and it's possible write to it.
     *
     * @param aTF text field with file name filled
     * @param aFileDescKey file description (used in error message)
     * @return result of the check
     */
    private boolean checkFileExists(JTextField aTF, String aFileDescKey) {
        final String tmpFileName = aTF.getText();
        try {
            if (tmpFileName != null) {
                File tmpFile = new File(tmpFileName);
                if (tmpFile.canRead() && !tmpFile.isDirectory()) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.fine(e.getMessage());
        }

        final String tmpMsg = RES.get("gui.fileNotExists.error", new String[] { RES.get(aFileDescKey) });
        JOptionPane.showMessageDialog(this, tmpMsg, RES.get("gui.check.error.title"), JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Checks if inFile and outFile are different.
     *
     * @return result of the check
     */
    private boolean checkInOutDiffers() {
        final String tmpInName = tfInPdfFile.getText();
        final String tmpOutName = tfOutPdfFile.getText();
        boolean tmpResult = true;
        if (tmpInName != null && StringUtils.isNotEmpty(tmpOutName)) {
            try {
                final File tmpInFile = (new File(tmpInName)).getAbsoluteFile();
                final File tmpOutFile = (new File(tmpOutName)).getAbsoluteFile();
                if (tmpInFile.equals(tmpOutFile)) {
                    tmpResult = false;
                    JOptionPane.showMessageDialog(this, RES.get("gui.filesEqual.error"), RES.get("gui.check.error.title"),
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                tmpResult = false;
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return tmpResult;
    }

    private boolean checkCertEncryption() {
        boolean tmpResult = true;
        if (chkbAdvanced.isSelected() && cbPdfEncryption.getSelectedItem() == PDFEncryption.CERTIFICATE) {
            tmpResult = checkFileExists(tfEncCertFile, "gui.encryptionCertFile.label");
            if (tmpResult && !KeyStoreUtils.isEncryptionSupported(KeyStoreUtils.loadCertificate(tfEncCertFile.getText()))) {
                tmpResult = false;
                JOptionPane.showMessageDialog(this, RES.get("gui.certificateNotForEncryption.error", tfEncCertFile.getText()),
                        RES.get("gui.check.error.title"), JOptionPane.ERROR_MESSAGE);
            }
        }
        return tmpResult;
    }

    /**
     * Handles pressing of "Sign It" button. Creates and runs SignerLogic instance in a new thread.
     *
     * @param evt event
     */
    private void btnSignItActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnSignItActionPerformed
        // ticket#3288393 (.pdf suffix)
        String tmpOut = tfOutPdfFile.getText();
        if (StringUtils.isNotEmpty(tmpOut)) {
            File tmpOutFile = new File(tmpOut);
            if (!tmpOut.toLowerCase().endsWith(".pdf") && !tmpOutFile.isFile()) {
                tfOutPdfFile.setText(tmpOut + ".pdf");
            }
        }
        storeToOptions();
        if (checkFileExists(tfInPdfFile, "gui.inPdfFile.label") && checkInOutDiffers() && checkCertEncryption()) {
            infoTextArea.setText(null);
            btnInfoClose.setEnabled(false);
            infoDialog.setVisible(true);
            setVisible(false);
            infoDialog.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

            LOGGER.info(RES.get("console.starting"));
            // Let's do it
            final Thread tmpST = new Thread(signerLogic);
            tmpST.start();
            UIManager.put("OptionPane.minimumSize",new Dimension(150,150));
            JOptionPane.showMessageDialog(null, "FIRMA REALIZADA EXITOSAMENTE", "AVISO", JOptionPane.INFORMATION_MESSAGE);
        }
    }// GEN-LAST:event_btnSignItActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowClosing
        try {
            storeToOptions();
            options.storeOptions();
        } catch (Exception e) {
            e.printStackTrace();
        }
        PKCS11Utils.unregisterProviders();
    }// GEN-LAST:event_formWindowClosing

    private void btnOutPdfFileActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnOutPdfFileActionPerformed
        showFileChooser(tfOutPdfFile, SignerFileChooser.FILEFILTER_PDF, JFileChooser.SAVE_DIALOG);
    }// GEN-LAST:event_btnOutPdfFileActionPerformed

    private void cbKeystoreTypeActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbKeystoreTypeActionPerformed
        refreshView();
        pack();
    }// GEN-LAST:event_cbKeystoreTypeActionPerformed

    private void btnInPdfFileActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnInPdfFileActionPerformed
        showFileChooser(tfInPdfFile, SignerFileChooser.FILEFILTER_PDF, JFileChooser.OPEN_DIALOG);
    }// GEN-LAST:event_btnInPdfFileActionPerformed

    private void btnKeystoreFileActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnKeystoreFileActionPerformed
        showFileChooser(tfKeystoreFile, null, JFileChooser.OPEN_DIALOG);
    }// GEN-LAST:event_btnKeystoreFileActionPerformed

    private void btnTsaOcspActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnTsaOcspActionPerformed
        tsaDialog.setVisible(true);
    }// GEN-LAST:event_btnTsaOcspActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEncCertFile;
    private javax.swing.JButton btnInPdfFile;
    private javax.swing.JButton btnInfoCertificationLevel;
    private javax.swing.JButton btnInfoClose;
    private javax.swing.JButton btnInfoKeystoreType;
    private javax.swing.JButton btnInfoOutPdfFile;
    private javax.swing.JButton btnInfoPdfEncryption;
    private javax.swing.JButton btnInfokeyAlias;
    private javax.swing.JButton btnKeystoreFile;
    private javax.swing.JButton btnLoadAliases;
    private javax.swing.JButton btnOutPdfFile;
    private javax.swing.JButton btnRights;
    private javax.swing.JButton btnRightsOK;
    private javax.swing.JButton btnSignIt;
    private javax.swing.JButton btnTsaOcsp;
    private javax.swing.JButton btnVisibleSigSettings;
    private javax.swing.JComboBox cbAlias;
    private javax.swing.JComboBox cbCertLevel;
    private javax.swing.JComboBox cbHashAlgorithm;
    private javax.swing.JComboBox cbKeystoreType;
    private javax.swing.JComboBox cbPdfEncryption;
    private javax.swing.JComboBox cbPrinting;
    private javax.swing.JCheckBox chkbAdvanced;
    private javax.swing.JCheckBox chkbAllowAssembly;
    private javax.swing.JCheckBox chkbAllowCopy;
    private javax.swing.JCheckBox chkbAllowFillIn;
    private javax.swing.JCheckBox chkbAllowModifyAnnotations;
    private javax.swing.JCheckBox chkbAllowModifyContent;
    private javax.swing.JCheckBox chkbAllowScreenReaders;
    private javax.swing.JCheckBox chkbAppendSignature;
    private javax.swing.JCheckBox chkbStorePwd;
    private javax.swing.JCheckBox chkbVisibleSig;
    private javax.swing.JFrame infoDialog;
    private javax.swing.JScrollPane infoScrollPane;
    private javax.swing.JTextArea infoTextArea;
    private javax.swing.JLabel lblAlias;
    private javax.swing.JLabel lblCertLevel;
    private javax.swing.JLabel lblContact;
    private javax.swing.JLabel lblEncCertFile;
    private javax.swing.JLabel lblHashAlgorithm;
    private javax.swing.JLabel lblInPdfFile;
    private javax.swing.JLabel lblKeyPwd;
    private javax.swing.JLabel lblKeystoreFile;
    private javax.swing.JLabel lblKeystorePwd;
    private javax.swing.JLabel lblKeystoreType;
    private javax.swing.JLabel lblLocation;
    private javax.swing.JLabel lblOutPdfFile;
    private javax.swing.JLabel lblPdfEncryption;
    private javax.swing.JLabel lblPdfOwnerPwd;
    private javax.swing.JLabel lblPdfUserPwd;
    private javax.swing.JLabel lblPrinting;
    private javax.swing.JLabel lblReason;
    private javax.swing.JLabel lblRights;
    private javax.swing.JPasswordField pfKeyPwd;
    private javax.swing.JPasswordField pfKeystorePwd;
    private javax.swing.JPasswordField pfPdfOwnerPwd;
    private javax.swing.JPasswordField pfPdfUserPwd;
    private javax.swing.JDialog rightsDialog;
    private javax.swing.JTextField tfContact;
    private javax.swing.JTextField tfEncCertFile;
    private javax.swing.JTextField tfInPdfFile;
    private javax.swing.JTextField tfKeystoreFile;
    private javax.swing.JTextField tfLocation;
    private javax.swing.JTextField tfOutPdfFile;
    private javax.swing.JTextField tfReason;
    // End of variables declaration//GEN-END:variables

}
