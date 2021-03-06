package title;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import Tools.ToolPanel;
import burp.BurpExtender;
import burp.Commons;
import burp.IMessageEditor;
import title.search.History;
import title.search.LineSearch;
import title.search.SearchDork;

public class LineTable extends JTable
{
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private LineTableModel lineTableModel;
	private TableRowSorter<LineTableModel> rowSorter;//TableRowSorter vs. RowSorter

	private IMessageEditor requestViewer;
	private IMessageEditor responseViewer;
	PrintWriter stdout;
	PrintWriter stderr;

	private JSplitPane tableAndDetailSplitPane;//table area + detail area
	public JSplitPane getTableAndDetailSplitPane() {
		return tableAndDetailSplitPane;
	}

	//将选中的行（图形界面的行）转换为Model中的行数（数据队列中的index）.因为图形界面排序等操作会导致图像和数据队列的index不是线性对应的。
	public int[] SelectedRowsToModelRows(int[] SelectedRows) {

		int[] rows = SelectedRows;
		for (int i=0; i < rows.length; i++){
			rows[i] = convertRowIndexToModel(rows[i]);//转换为Model的索引，否则排序后索引不对应〿
		}
		Arrays.sort(rows);//升序

		return rows;
	}

	public LineTable(LineTableModel lineTableModel)
	{
		//super(lineTableModel);//这个方法创建的表没有header
		try{
			stdout = new PrintWriter(BurpExtender.getCallbacks().getStdout(), true);
			stderr = new PrintWriter(BurpExtender.getCallbacks().getStderr(), true);
		}catch (Exception e){
			stdout = new PrintWriter(System.out, true);
			stderr = new PrintWriter(System.out, true);
		}

		this.lineTableModel = lineTableModel;
		this.setFillsViewportHeight(true);//在table的空白区域显示右键菜单
		//https://stackoverflow.com/questions/8903040/right-click-mouselistener-on-whole-jtable-component
		this.setModel(lineTableModel);

		tableinit();
		//FitTableColumns(this);
		addClickSort();
		registerListeners();

		tableAndDetailSplitPane = tableAndDetailPanel();
	}

	@Override
	public void changeSelection(int row, int col, boolean toggle, boolean extend)
	{
		// show the log entry for the selected row
		LineEntry Entry = this.lineTableModel.getLineEntries().getValueAtIndex(super.convertRowIndexToModel(row));

		requestViewer.setMessage(Entry.getRequest(), true);
		responseViewer.setMessage(Entry.getResponse(), false);
		this.lineTableModel.setCurrentlyDisplayedItem(Entry);
		super.changeSelection(row, col, toggle, extend);
	}

	@Override
	public LineTableModel getModel(){
		//return (LineTableModel) super.getModel();
		return lineTableModel;
	}


	public JSplitPane tableAndDetailPanel(){
		JSplitPane splitPane = new JSplitPane();//table area + detail area
		splitPane.setResizeWeight(0.5);
		splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		//TitlePanel.add(splitPane, BorderLayout.CENTER); // getTitlePanel to get it

		JScrollPane scrollPaneRequests = new JScrollPane(this,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);//table area
		//允许横向滚动条
		//scrollPaneRequests.setViewportView(titleTable);//titleTable should lay here.
		splitPane.setLeftComponent(scrollPaneRequests);

		JSplitPane RequestDetailPanel = new JSplitPane();//request and response
		RequestDetailPanel.setResizeWeight(0.5);
		splitPane.setRightComponent(RequestDetailPanel);

		JTabbedPane RequestPanel = new JTabbedPane();
		RequestDetailPanel.setLeftComponent(RequestPanel);

		JTabbedPane ResponsePanel = new JTabbedPane();
		RequestDetailPanel.setRightComponent(ResponsePanel);

		requestViewer = BurpExtender.getCallbacks().createMessageEditor(this.getModel(), false);
		responseViewer = BurpExtender.getCallbacks().createMessageEditor(this.getModel(), false);
		RequestPanel.addTab("Request", requestViewer.getComponent());
		ResponsePanel.addTab("Response", responseViewer.getComponent());

		this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);//配合横向滚动条

		return splitPane;
	}

	public void tableinit(){
		//Font f = new Font("Arial", Font.PLAIN, 12);
		Font f = this.getFont();
		FontMetrics fm = this.getFontMetrics(f);
		int width = fm.stringWidth("A");//一个字符的宽度


		Map<String,Integer> preferredWidths = new HashMap<String,Integer>();
		preferredWidths.put("#",5);
		preferredWidths.put("URL",25);
		preferredWidths.put("Status",6);
		preferredWidths.put("Length",10);
		preferredWidths.put("Title",30);
		preferredWidths.put("Comments",30);
		preferredWidths.put("Time","2019-05-28-14-13-16".length());
		preferredWidths.put("isChecked"," isChecked ".length());
		preferredWidths.put("IP",30);
		preferredWidths.put("CDN|CertInfo",30);
		preferredWidths.put("Server",10);
		for(String header:LineTableModel.getTitletList()){
			try{//避免动态删除表字段时，出错
				int multiNumber = preferredWidths.get(header);
				this.getColumnModel().getColumn(this.getColumnModel().getColumnIndex(header)).setPreferredWidth(width*multiNumber);
			}catch (Exception e){

			}
		}
		//this.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);//配合横向滚动条

	}

	@Deprecated//据说自动调整行宽度，测试了一下没用啊
	public void FitTableColumns(JTable myTable){
		JTableHeader header = myTable.getTableHeader();
		int rowCount = myTable.getRowCount();
		Enumeration columns = myTable.getColumnModel().getColumns();
		while(columns.hasMoreElements()){
			TableColumn column = (TableColumn)columns.nextElement();
			int col = header.getColumnModel().getColumnIndex(column.getIdentifier());
			int width = (int)myTable.getTableHeader().getDefaultRenderer()
					.getTableCellRendererComponent(myTable, column.getIdentifier()
							, false, false, -1, col).getPreferredSize().getWidth();
			for(int row = 0; row<rowCount; row++){
				int preferedWidth = (int)myTable.getCellRenderer(row, col).getTableCellRendererComponent(myTable,
						myTable.getValueAt(row, col), false, false, row, col).getPreferredSize().getWidth();
				width = Math.max(width, preferedWidth);
			}
			header.setResizingColumn(column); // 此行很重要
			column.setWidth(width+myTable.getIntercellSpacing().width);
		}
	}

	//TODO,还没弄明白
	@Deprecated
	public void setColor(int inputRow) {
		try {
			DefaultTableCellRenderer dtcr = new DefaultTableCellRenderer() {
				//重写getTableCellRendererComponent 方法
				@Override
				public Component getTableCellRendererComponent(JTable table,Object value, boolean isSelected, boolean hasFocus,int row, int column) {
					Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
					if (row == 1) {
						c.setBackground(Color.RED);
					}
					return c;
				}
			};
			//对每行的每一个单元格
			int columnCount = this.getColumnCount();
			for (int i = 0; i < columnCount; i++) {
				this.getColumn(this.getColumnName(i)).setCellRenderer(dtcr);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addClickSort() {//双击header头进行排序

		rowSorter = new TableRowSorter<LineTableModel>(lineTableModel);//排序和搜索
		LineTable.this.setRowSorter(rowSorter);

		JTableHeader header = this.getTableHeader();
		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					LineTable.this.getRowSorter().getSortKeys().get(0).getColumn();
					////当Jtable中无数据时，jtable.getRowSorter()是nul
				} catch (Exception e1) {
					e1.printStackTrace(stderr);
				}
			}
		});
	}

	//搜索功能函数
	public void search(String Inputkeyword) {
		//rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + keyword));
		History.getInstance().addRecord(Inputkeyword);//记录搜索历史,单例模式

		Inputkeyword = Inputkeyword.trim().toLowerCase();
		if (Inputkeyword.contains("\"") || Inputkeyword.contains("\'")){
			//为了处理输入是"dork:12345"的情况，下面的这种写法其实不严谨，中间也可能有引号，不过应付一般的搜索足够了。
			Inputkeyword = Inputkeyword.replaceAll("\"", "");
			Inputkeyword = Inputkeyword.replaceAll("\'", "");
		}

		String dork = SearchDork.grepDork(Inputkeyword);
		String keyword =  SearchDork.grepKeyword(Inputkeyword);

		//stdout.println("dork:"+dork+"   keyword:"+keyword);
		final RowFilter filter = new RowFilter() {
			@Override
			public boolean include(Entry entry) {
				//entry --- a non-null object that wraps the underlying object from the model
				int row = (int) entry.getIdentifier();
				LineEntry line = rowSorter.getModel().getLineEntries().getValueAtIndex(row);

				//第一层判断，根据按钮状态进行判断，如果为true，进行后面的逻辑判断，false直接返回。
				if (!LineSearch.entryNeedToShow(line)) {
					return false;
				}

				if (SearchDork.isDork(dork)) {
					//stdout.println("do dork search,dork:"+dork+"   keyword:"+keyword);
					return LineSearch.dorkFilte(line,dork,keyword);
				}else {
					return LineSearch.textFilte(line,keyword);
				}
			}
		};
		rowSorter.setRowFilter(filter);
	}

	
	public void registerListeners(){
		LineTable.this.setRowSelectionAllowed(true);
		this.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e) {
				//双击进行google搜索、双击浏览器打开url、双击切换Check状态
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2){//左键双击
					int[] rows = SelectedRowsToModelRows(getSelectedRows());

					//int row = ((LineTable) e.getSource()).rowAtPoint(e.getPoint()); // 获得行位置
					int col = ((LineTable) e.getSource()).columnAtPoint(e.getPoint()); // 获得列位置

					LineEntry selecteEntry = LineTable.this.lineTableModel.getLineEntries().getValueAtIndex(rows[0]);
					if ((col==0 )) {//双击index在google中搜索host。
						String host = selecteEntry.getHost();
						String url= "https://www.google.com/search?q=site%3A"+host;
						try {
							URI uri = new URI(url);
							Desktop desktop = Desktop.getDesktop();
							if(Desktop.isDesktopSupported()&&desktop.isSupported(Desktop.Action.BROWSE)){
								desktop.browse(uri);
							}
						} catch (Exception e2) {
							e2.printStackTrace();
						}
					}else if(col==1) {//双击url在浏览器中打开
						try{
							String url = selecteEntry.getUrl();
							Commons.browserOpen(url,ToolPanel.getLineConfig().getBrowserPath());
						}catch (Exception e1){
							e1.printStackTrace(stderr);
						}
					}else if (col == LineTableModel.getTitletList().indexOf("isChecked")) {
						try{
							//LineTable.this.lineTableModel.updateRowsStatus(rows,LineEntry.CheckStatus_Checked);//处理多行
							String currentStatus= selecteEntry.getCheckStatus();
							List<String> tmpList = Arrays.asList(LineEntry.CheckStatusArray);
							int index = tmpList.indexOf(currentStatus);
							String newStatus = tmpList.get((index+1)%3);
							selecteEntry.setCheckStatus(newStatus);
							stdout.println("$$$ "+selecteEntry.getUrl()+" status has been set to "+LineEntry.CheckStatus_Checked);
							LineTable.this.lineTableModel.fireTableRowsUpdated(rows[0], rows[0]);
						}catch (Exception e1){
							e1.printStackTrace(stderr);
						}
					}else if (col == LineTableModel.getTitletList().indexOf("Level")) {
						String currentLevel = selecteEntry.getLevel();
						List<String> tmpList = Arrays.asList(LineEntry.LevelArray);
						int index = tmpList.indexOf(currentLevel);
						String newLevel = tmpList.get((index+1)%3);
						selecteEntry.setLevel(newLevel);
						stdout.println(String.format("$$$ %s updated [level-->%s]",selecteEntry.getUrl(),newLevel));
						LineTable.this.lineTableModel.fireTableRowsUpdated(rows[0], rows[0]);
					}
				}
			}

			@Override//title表格中的鼠标右键菜单
			public void mouseReleased( MouseEvent e ){//在windows中触发,因为isPopupTrigger在windows中是在鼠标释放是触发的，而在mac中，是鼠标点击时触发的。
				//https://stackoverflow.com/questions/5736872/java-popup-trigger-in-linux
				if ( SwingUtilities.isRightMouseButton( e )){
					if (e.isPopupTrigger() && e.getComponent() instanceof JTable ) {
						//getSelectionModel().setSelectionInterval(rows[0], rows[1]);
						int[] rows = getSelectedRows();
						int col = ((LineTable) e.getSource()).columnAtPoint(e.getPoint()); // 获得列位置
						if (rows.length>0){
							rows = SelectedRowsToModelRows(getSelectedRows());
							new LineEntryMenu(LineTable.this, rows, col).show(e.getComponent(), e.getX(), e.getY());
						}else{//在table的空白处显示右键菜单
							//https://stackoverflow.com/questions/8903040/right-click-mouselistener-on-whole-jtable-component
							//new LineEntryMenu(_this).show(e.getComponent(), e.getX(), e.getY());
						}
					}
				}
			}

			@Override
			public void mousePressed(MouseEvent e) { //在mac中触发
				mouseReleased(e);
			}

		});

	}
}
