<<:com_color0_start:>>
<tr bgcolor="#FFFFFF" class="table_row">
<<:com_color0_end:>>
<<:com_color1_start:>>
<tr bgcolor="#F0F0F0" class="table_row">
<<:com_color1_end:>>
    <td align=right class="table_row" style="padding-right:3px" sorttable_customkey="<<:seriescount esc:>>"><<:!com_separate_start:>><<:seriescount esc:>>.<<:!com_separate_end:>></td>
    <td nowrap align=left class="table_row" sorttable_customkey="<<:name esc:>>">
        <<:com_seriescolor_start:>>
            <table border=0 cellspacing=0 cellpadding=0>
                <tr>
                    <td nowrap valign=absmiddle align=center>
                        <table border=0 cellspacing=0 cellpadding=0>
                            <tr height=10>
                                <td nowrap width=10 height=10 bgcolor="<<:seriescolor esc:>>"><img src="/img/empty.gif" width=10 height=10></td>
                            </tr>
                        </table>
                    </td>
                    <td><<:annotations:>></td>
                    <td nowrap>&nbsp;<a class="link" onMouseOver="return overlib('Show only <b><<:name js:>></b>'+ ('<<:name js:>>'.toLowerCase()=='<<:realname js:>>'.toLowerCase() ? '' : ' (<<:realname js:>>)'));" onMouseOut="return nd();" href="JavaScript:so('<<:realname js:>>');"><b><<:name esc:>></b></a>&nbsp;</td>
                </tr>
            </table>
        <<:com_seriescolor_end:>><<:com_noseriescolor_start:>><a title="<<:realname esc:>>"><<:name esc:>></a>&nbsp;<<:com_noseriescolor_end:>>
    </td>
    <td nowrap align=right class="table_row" sorttable_customkey="<<:sorttable_last esc:>>">&nbsp;&nbsp;<<:last esc:>>&nbsp;</td>
    <td nowrap align=right class="table_row" sorttable_customkey="<<:sorttable_min esc:>>">&nbsp;&nbsp;<<:min esc:>>&nbsp;</td>
    <td nowrap align=right class="table_row" sorttable_customkey="<<:sorttable_avg esc:>>">&nbsp;&nbsp;<<:avg esc:>>&nbsp;</td>
    <td nowrap align=right class="table_row" sorttable_customkey="<<:sorttable_max esc:>>">&nbsp;&nbsp;<<:max esc:>>&nbsp;</td>
    <<:com_total_start:>>
    <td nowrap align=right class="table_row" sorttable_customkey="<<:sorttable_total esc:>>">&nbsp;&nbsp;<<:total esc:>>&nbsp;</td>
    <<:com_total_end:>>
</tr>
