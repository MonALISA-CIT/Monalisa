package lia.Monitor.JiniSerFarmMon;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import lia.Monitor.monitor.DataStore;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;
import net.jini.lookup.entry.UIDescriptor;
import net.jini.lookup.ui.MainUI;
import net.jini.lookup.ui.attribute.RequiredPackages;
import net.jini.lookup.ui.attribute.UIFactoryTypes;
import net.jini.lookup.ui.factory.JComponentFactory;

public class FarmMonGui extends JPanel implements Entry {

    /**
     * 
     */
    private static final long serialVersionUID = -5699504163703796634L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(FarmMonGui.class.getName());

    /**
     * A reference to out service's proxy
     */
    protected DataStore proxy;
    JPanel jPanel1 = new JPanel();
    BorderLayout borderLayout1 = new BorderLayout();
    JLabel jLabel1 = new JLabel();
    JPasswordField jPasswordField1 = new JPasswordField();
    JList jList_passwords = new JList();
    JPanel jPanel2 = new JPanel();
    JTextField jTextField_pwd = new JTextField();
    JButton jButton1 = new JButton();
    JButton jButton2 = new JButton();
    JButton jButton3 = new JButton();
    JTextField jTextField_pwd_name = new JTextField();
    JButton jButton4 = new JButton();

    /**
     * Get a UIDescriptor as needed by the serviceui-draft-standart
     */
    public static UIDescriptor getUIDescriptor() {
        try {
            return (new UIDescriptor(MainUI.ROLE, JComponentFactory.TOOLKIT, getServiceUIProperties(),
                    new MarshalledObject(new Factory())));
        } catch (IOException e) {
            //     e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception ", e);
        }
        return (null);
    }

    /**
     * Gets a serviceui-draft-standart-compactible attribute-set of this representation
     */
    static Set getServiceUIProperties() {
        HashSet set = new HashSet();
        set.add("javax.swing.JComponent");
        set.add("javax.swing.JPanel");
        set.add("java.awt.Component");

        HashSet retval = new HashSet();
        retval.add(new UIFactoryTypes(set));

        HashMap map = new HashMap();
        map.put("javax.swing", Package.getPackage("javax.swing").getSpecificationVersion());
        RequiredPackages req = new RequiredPackages(map);
        retval.add(req);

        return (retval);
    }

    public static class Factory implements JComponentFactory, Serializable {

        /**
         * Returns a <CODE>JComponent</CODE>.
         */
        @Override
        public JComponent getJComponent(Object roleObject) {
            ServiceItem item = (ServiceItem) roleObject;

            try {
                return ((new FarmMonGui(((DataStore) item.service))).getPanel());
            } catch (RemoteException ex) {
                return (null);
            }

        }
    }

    public JPanel getPanel() throws RemoteException {
        return (this);
    }

    /**
     * Constructor
     */
    public FarmMonGui(DataStore proxy) {
        try {
            this.proxy = proxy;

            jbInit();
            updateList();
        } catch (Exception e) {
            //     e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception", e);
        }
    }

    @Override
    public void setFont(Font f) {
        if (jLabel1 == null) {
            return;
        }
        if (f == null) {
            return;
        }

        super.setFont(f);
        jLabel1.setFont(f);
        jPanel1.setFont(f);
        jPanel2.setFont(f);
        jPasswordField1.setFont(f);
        jTextField_pwd.setFont(f);
        jTextField_pwd_name.setFont(f);
        jList_passwords.setFont(f);
        jButton1.setFont(f);
        jButton2.setFont(f);
        jButton3.setFont(f);
        jButton4.setFont(f);
    };

    /** Create UI.
      */
    private void jbInit() throws Exception {
        this.setLayout(borderLayout1);
        jLabel1.setText("master-password:");
        jPasswordField1.setText("testmaster");
        jTextField_pwd.setText("                 ");
        jButton1.setText("get");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButton1_actionPerformed(e);
            }
        });
        jButton2.setText("store");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButton2_actionPerformed(e);
            }
        });
        jButton3.setToolTipText("");
        jButton3.setText("copy pwd");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButton3_actionPerformed(e);
            }
        });
        jTextField_pwd_name.setText("pwd-name");
        jButton4.setText("set master");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButton4_actionPerformed(e);
            }
        });
        this.add(jPanel1, BorderLayout.NORTH);
        jPanel1.add(jButton4, null);
        jPanel1.add(jLabel1, null);
        jPanel1.add(jPasswordField1, null);
        this.add(jList_passwords, BorderLayout.CENTER);
        this.add(jPanel2, BorderLayout.SOUTH);
        jPanel2.add(jButton1, null);
        jPanel2.add(jTextField_pwd_name, null);
        jPanel2.add(jButton2, null);
        jPanel2.add(jTextField_pwd, null);
        jPanel2.add(jButton3, null);
    }

    void updateList() {
        try {
            char pwdc[] = jPasswordField1.getPassword();
            byte pwd[] = new byte[pwdc.length];
            for (int i = 0; i < pwdc.length; i++) {
                pwd[i] = (byte) pwdc[i];
            }

            DefaultListModel lm = new DefaultListModel();
            /*
                  String           data[] = proxy.getPasswordIdentifiers(pwd);
                  for(int i=0; i<data.length; i++)
                    lm.addElement(data[i]);
                  jList_passwords.setModel(lm);
            */
        } catch (Exception e) {
            //      e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception", e);
        }
    }

    void jButton2_actionPerformed(ActionEvent evt) {
        try {
            char pwdc[] = jPasswordField1.getPassword();
            byte pwd[] = new byte[pwdc.length];
            for (int i = 0; i < pwdc.length; i++) {
                pwd[i] = (byte) pwdc[i];
            }
            //proxy.setPassword(jTextField_pwd_name.getText(), jTextField_pwd.getText(), pwd);
            updateList();
        } catch (Exception e) {
            //      e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception", e);
        }
    }

    void jButton1_actionPerformed(ActionEvent evt) {
        try {
            /*
                  char pwdc[] = jPasswordField1.getPassword();
                  byte pwd[] = new byte[pwdc.length];
                  for(int i=0; i<pwdc.length; i++)
                    pwd[i] = (byte)pwdc[i];
                  String pwds = proxy.getPassword(jTextField_pwd_name.getText(), pwd);
                  jTextField_pwd.setText(pwds);
            */
        } catch (Exception e) {
            //      e.printStackTrace();
            logger.log(Level.WARNING, "Got exception", e);
        }
    }

    void jButton3_actionPerformed(ActionEvent evt) {
        try {
            char pwdc[] = jPasswordField1.getPassword();
            byte pwd[] = new byte[pwdc.length];
            for (int i = 0; i < pwdc.length; i++) {
                pwd[i] = (byte) pwdc[i];
            }
            //String pwds = proxy.getPassword(jTextField_pwd_name.getText(), pwd);
            //jTextField_pwd.setText(pwds);
            jTextField_pwd.copy();
            jTextField_pwd.setText("cucuc");
        } catch (Exception e) {
            //      e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception", e);
        }
    }

    void jButton4_actionPerformed(ActionEvent evt) {
        try {
            char pwdc[] = jPasswordField1.getPassword();
            byte pwd[] = new byte[pwdc.length];
            for (int i = 0; i < pwdc.length; i++) {
                pwd[i] = (byte) pwdc[i];
            }
            //proxy.setMasterPassword(jTextField_pwd.getText().getBytes(), pwd);
            jPasswordField1.setText(jTextField_pwd.getText());
        } catch (Exception e) {
            //      e.printStackTrace();
            logger.log(Level.WARNING, "Got Exception", e);
        }
    }

}
