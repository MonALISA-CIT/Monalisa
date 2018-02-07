<tr bgcolor="<<:color:>>" class="table_row">
    <td align=right class="table_row"><<:i:>>.</td>
    <td align=left class="table_row"><<:name:>>&nbsp;</td>
    <td align=left class="table_row"><a onMouseOver="return overlib('<<:ip js:>>', CAPTION, 'Click for details');" onMouseOut="return nd();" onClick="nd(); showCenteredWindow('<div style=\'padding-left:10px;padding-top:10px\'>IP: <<:ip js:>><br>Reverse name: <<:reverseip js:>></div>', 'Service <<:name js:>>'); return false;" class="link"><<:reverseip:>></a>&nbsp;</td>
    <td align=right class="table_row"><<:version:>>&nbsp;</td>
    <td align=right class="table_row"><<:geo_lat cut8:>>&nbsp;</td>
    <td align=right class="table_row"><<:geo_long cut8:>>&nbsp;</td>
    <td align=center class="table_row"><font color=<<:on_color:>>><b><<:on:>></b>&nbsp;</td>
    <td align=right class="table_row">
	<a class="link" href="admin.jsp?delete=<<:name_link:>>&ip=<<:ip_link:>>" onclick="return confirm('Are you sure that you want to delete this service ?');">delete</a> |
	<a class="link" href="admin.jsp?ban=<<:name_link:>>&ip=<<:ip_link:>>" onclick="return confirm('Are you sure that you want to ban this service?');">ban</a>
    </td>
    <td align=center class="table_row" bgcolor="<<:color_web:>>">
	<a class="link" href="admin.jsp?hide=<<:name_link:>>"><<:msg_web:>></a>
    </td>
</tr>
