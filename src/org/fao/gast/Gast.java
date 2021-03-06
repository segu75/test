//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.gast;

import java.io.File;
import java.net.URL;
import org.fao.gast.boot.Util;

//=============================================================================

public class Gast
{
	//---------------------------------------------------------------------------
	//---
	//--- Constructor
	//---
	//---------------------------------------------------------------------------

	public static void main(String args[]) throws Exception
	{
		String jarFile = Util.getJarFile("org/fao/gast/Gast.class");
		String appPath = getPath(jarFile);
		URL[]  jars    = Util.getJarUrls(appPath +"/web/geonetwork/WEB-INF/lib");

		String starter = (args.length == 0)
									? "org.fao.gast.gui.MainFrame"
									: "org.fao.gast.cli.Cli";

		Util.boot(appPath, jars, starter, args);
	}

	//---------------------------------------------------------------------------

	private static String getPath(String jarFile)
	{
		//--- we suppose that the GAST jar file is inside the "gast" folder

		return new File(jarFile).getParentFile().getParentFile().getAbsolutePath();
	}
}

//=============================================================================

