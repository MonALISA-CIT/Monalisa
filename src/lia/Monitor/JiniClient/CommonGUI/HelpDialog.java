package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;


public class HelpDialog extends JDialog
{

    URL m_defaultURL = null;
    URL m_currentURL = null;
    Frame  main= null;
    JEditorPane helpEditorPane = null;
    JPanel buttonPanel = null;
    JButton backButton = null;
    JButton forwardButton = null;
    JPanel statusPanel = null;
    JLabel statusLabel = null;
    BoxLayout2 boxLayout21 = null;
    BorderLayout borderLayout1 = null;
    BoxLayout2 boxLayout22 = null;
    Border border1 = null;
    Stack backStack = null;
    Stack forwardStack = null;

    public HelpDialog( Frame  main)
    {
        m_defaultURL = null;
        m_currentURL = null;
        helpEditorPane = new JEditorPane();
        buttonPanel = new JPanel();
        backButton = new JButton();
        forwardButton = new JButton();
        statusPanel = new JPanel();
        statusLabel = new JLabel();
        boxLayout21 = new BoxLayout2();
        borderLayout1 = new BorderLayout();
        boxLayout22 = new BoxLayout2();
        backStack = new Stack();
        forwardStack = new Stack();
      this.main = main;
        getContentPane().setLayout(borderLayout1);
        setTitle("Help");
        forwardButton.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        forwardButton.setRequestFocusEnabled(false);
        backButton.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        backButton.setRequestFocusEnabled(false);
        backButton.setPreferredSize(new Dimension(85, 20));
//        backButton.setIcon(new ImageIcon(getClass().getResource("/image/leftLine.gif")));
//        forwardButton.setIcon(new ImageIcon(getClass().getResource("/image/rightLine.gif")));
        backButton.setToolTipText("Back");
        forwardButton.setToolTipText("Forward");
        forwardButton.setPreferredSize(new Dimension(85, 20));
        statusPanel.setLayout(boxLayout21);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 0));
        buttonPanel.setPreferredSize(new Dimension(159, 27));
        buttonPanel.setLayout(boxLayout22);
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setPreferredSize(new Dimension(41, 20));
        getContentPane().add(new JScrollPane(helpEditorPane), "Center");
        getContentPane().add(buttonPanel, "North");
        buttonPanel.add(backButton, null);
        buttonPanel.add(forwardButton, null);
        getContentPane().add(statusPanel, "South");
        statusPanel.add(statusLabel, null);
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        helpEditorPane.setEditable(false);
        setResizable(true);
        setModal(true);
        setDefaultCloseOperation(2);
        pack();
        helpEditorPane.addHyperlinkListener(new HyperlinkListener() {

            public void hyperlinkUpdate(HyperlinkEvent e)
            {
                try
                {
                    m_currentURL = e.getURL();
                    if (m_currentURL != null)
                    {
                        statusLabel.setText(m_currentURL.toString());
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }

        });
        helpEditorPane.addMouseListener(new MouseAdapter() {

            public void mouseClicked(MouseEvent e)
            {
                try
                {
                    if (m_currentURL == null)
                    {
                        return;
                    }
                    backButton.setEnabled(true);
                    forwardButton.setEnabled(false);
                    forwardStack.clear();
                    if (!backStack.contains(m_currentURL))
                    {
                        backStack.push(m_currentURL);
                    }
                    helpEditorPane.setPage(m_currentURL);
                }
                catch (Exception ex)
                {
                   // ex.printStackTrace();
                }
            }

        });
        backButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if (!forwardButton.isEnabled())
                    {
                        forwardStack.push(backStack.pop());
                    }
                    forwardButton.setEnabled(true);
                    if (backStack.empty())
                    {
                        backStack.push(m_defaultURL);
                        helpEditorPane.setPage(m_defaultURL);
                        backButton.setEnabled(false);
                        return;
                    }
                    URL u = (URL)backStack.pop();
                    forwardStack.push(u);
                    helpEditorPane.setPage(u);
                    if (backStack.empty())
                    {
                        backStack.push(m_defaultURL);
                        helpEditorPane.setPage(m_defaultURL);
                        backButton.setEnabled(false);
                    }
                }
                catch (Exception ex)
                {
                    //ex.printStackTrace();
                }
            }

        });
        forwardButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if (!backButton.isEnabled())
                    {
                        forwardStack.pop();
                    }
                    backButton.setEnabled(true);
                    if (forwardStack.empty())
                    {
                        forwardButton.setEnabled(false);
                        return;
                    }
                    URL u = (URL)forwardStack.pop();
                    backStack.push(u);
                    helpEditorPane.setPage(u);
                }
                catch (Exception ex)
                {
                    //ex.printStackTrace();
                }
                if (forwardStack.empty())
                {
                    forwardButton.setEnabled(false);
                }
            }

        });
    }

    public void showOnlineHelp(String urlPage)
        throws IOException
    {
        m_defaultURL = new URL(urlPage);
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        backStack.clear();
        forwardStack.clear();
        backStack.add(m_defaultURL);
        helpEditorPane.setPage(m_defaultURL);
        statusLabel.setText(m_defaultURL.toString());
        //setLocationRelativeTo(m_browser);
        setSize(700, 650);
        show();
    }
}
