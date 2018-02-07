<tr height=25 class="table_row" bgcolor="<<:tdcolor:>>" id="tr_<<:source:>>">
    <td class="table_row">&nbsp;<<:source:>></td>
    <td class="table_row">
	<select name="destination_<<:source:>>" class="input_submit" id="destination_<<:source:>>">
	    <option value="-1">-Destination-</option>
	    <<:destination:>>
	</select>
    </td>
    <td class="table_row" bgcolor="#<<:color:>>" id="input_image_<<:source:>>"><input type="text" name="color" class="input_text" value="#<<:color_value:>>" size="7" id="tier_color_<<:source:>>"></td>
    <td class="table_row" ><div id="color_image_div_<<:source:>>"><img src="/js/colors/media/ezcolorpickericon.gif" onclick="load_picker('<<:source:>>')" name="color_image_<<:source:>>" id="color_image_<<:source:>>" style="background-color: #<<:color:>>"></div></td>
    <td class="table_row" align="center"><input type="text" name="tier_<<:source:>>" id="tier_<<:source:>>" value="<<:centertype:>>" class="input_text" size="1"></td>    
    <td class="table_row"><a href="javascript: void(0)" class="link" onclick="insertRelation('<<:source:>>');">Modify</a></td>
</tr>
