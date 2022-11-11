package domain.target;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.PrintWriter;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import burp.BurpExtender;
import burp.Commons;
import burp.DomainNameUtils;
import burp.IPAddressUtils;
import config.ConfigPanel;
import domain.DomainPanel;

public class TargetEntryMenu extends JPopupMenu {


	private static final long serialVersionUID = 1L;
	PrintWriter stdout = BurpExtender.getStdout();
	PrintWriter stderr = BurpExtender.getStderr();
	private static TargetTable rootDomainTable;

	public TargetEntryMenu(final TargetTable rootDomainTable, final int[] modelRows, final int columnIndex){
		this.rootDomainTable = rootDomainTable;

		JMenuItem getSubDomainsOf = new JMenuItem(new AbstractAction("Get All Subdomin Of This") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				String results = "";
				for (int row:modelRows) {
					String rootDomain = (String) rootDomainTable.getTargetModel().getValueAt(row,0);
					String line = DomainPanel.getDomainResult().fetchSubDomainsOf(rootDomain);
					results = results+System.lineSeparator()+line;
				}

				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(results);
				clipboard.setContents(selection, null);
			}
		});

		JMenuItem whoisItem = new JMenuItem(new AbstractAction("Whois") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				for (int row:modelRows) {
					String rootDomain = (String) rootDomainTable.getTargetModel().getValueAt(row,0);
					try {
						Commons.browserOpen("https://whois.chinaz.com/"+rootDomain,null);
						Commons.browserOpen("https://www.whois.com/whois/"+rootDomain,null);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});

		JMenuItem ASNInfoItem = new JMenuItem(new AbstractAction("ASN Info") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				for (int row:modelRows) {
					String target = (String) rootDomainTable.getTargetModel().getValueAt(row,0);

					try {
						//https://bgp.he.net/dns/shopee.com
						//https://bgp.he.net/net/143.92.111.0/24
						//https://bgp.he.net/ip/143.92.127.1
						String url =null;
						if (IPAddressUtils.isValidIP(target)){
							url = "https://bgp.he.net/ip/"+target;
						}
						if (IPAddressUtils.isValidSubnet(target)){
							url = "https://bgp.he.net/net/"+target;
						}
						if (DomainNameUtils.isValidDomain(target)){
							url = "https://bgp.he.net/dns/"+target;
						}
						if (url!= null){
							Commons.browserOpen(url,null);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});

		JMenuItem OpenWithBrowserItem = new JMenuItem(new AbstractAction("Open With Browser") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				for (int row:modelRows) {
					String rootDomain = (String) rootDomainTable.getTargetModel().getValueAt(row,0);
					try {
						Commons.browserOpen("https://"+rootDomain, ConfigPanel.getLineConfig().getBrowserPath());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});

		JMenuItem batchAddCommentsItem = new JMenuItem(new AbstractAction("Add Comments") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				String Comments = JOptionPane.showInputDialog("Comments", null).trim();
				while(Comments.trim().equals("")){
					Comments = JOptionPane.showInputDialog("Comments", null).trim();
				}
				rootDomainTable.getTargetModel().updateComments(modelRows,Comments);
			}
		});

		JMenuItem addToBlackItem = new JMenuItem(new AbstractAction("Add To Black List") {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				TargetControlPanel.selectedToBalck();
			}
		});

		this.add(getSubDomainsOf);
		this.add(whoisItem);
		this.add(ASNInfoItem);
		this.add(OpenWithBrowserItem);
		this.add(batchAddCommentsItem);
		this.add(addToBlackItem);
	}

}
