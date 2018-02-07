<tr bgcolor="<<:color:>>" onMouseOver="this.style.backgroundColor='#FFFFA2';" onMouseOut="this.style.backgroundColor='<<:color:>>'">
    <td bgcolor="<<:a_color:>>"><<:a_id:>></td>
    <td><<:a_groupnames:>></td>
    <td>
	<font color="<<:a_color:>>"><<:a_text:>></font>
	<<:com_image_start:>>
    	    <img align=absmiddle src=/img/qm.gif border=0 onMouseOver="return overlib('<<:a_description:>>', CAPTION, 'Click for details')" onMouseOut='return nd();' onClick="nd(); showCenteredWindow('<<:a_description:>>', 'Event description'); return false;">
        <<:com_image_end:>>
    </td>
    <td><<:a_service:>></td>
    <td>
	<<:diff:>>
    </td>
    <td><<:a_from:>></td>
    <td><<:a_to:>></td>
    <td><a href="#" onClick="editId(<<:a_id:>>); return false;"><img src="/img/edit.gif" border=0 onMouseOver="return overlib('Edit this entry');" onMouseOut="return nd();"></a>&nbsp;
	<a href="#" onClick="deleteId(<<:a_id:>>); return false"><img src="/img/editdelete.gif" border=0 onMouseOver="return overlib('Delete this entry');" onMouseOut="return nd();"></a>
    </td>
</tr>
		
