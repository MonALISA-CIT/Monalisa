package lia.web.servlets.wap;

import java.util.Properties;
import java.util.Vector;

import lia.web.servlets.web.Utils;
import lia.web.utils.Page;

/**
 */
public class index extends WAPPage {
	private static final long serialVersionUID = 1L;

	@Override
	public void execGet() {

		pMaster.modify("id", "index");
		pMaster.modify("title", "Repository Index");

		Page p = new Page(sResDir + "index/index.res");

		try {
			Properties prop = Utils.getProperties(sConfDir, "wap");

			Vector<String> vPages = toVector(prop, "pages", null);
			Vector<String> vDescr = toVector(prop, "descriptions", null);

			Page p2 = new Page(sResDir + "index/link.res");

			for (int i = 0; i < vPages.size(); i++) {
				String sPage = vPages.get(i);

				String sDesc = vDescr.size() > i ? (String) vDescr.get(i)
						: sPage;

				p2.modify("page", sPage);
				p2.modify("desc", sDesc);
				p2.modify("rand", nextRand());

				p.append(p2);
			}
		} catch (Exception e) {
			// ignore
		}

		pMaster.append(p);

		pMaster.write();
	}

}
