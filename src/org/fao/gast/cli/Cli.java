//==============================================================================
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

package org.fao.gast.cli;

import java.util.ArrayList;
import java.util.List;
import org.fao.gast.boot.Starter;
import org.fao.gast.cli.createdb.CreateDB;
import org.fao.gast.cli.setup.Setup;
import org.fao.gast.lib.Lib;

//==============================================================================

public class Cli implements Starter
{
	public void start(String appPath, String args[]) throws Exception
	{
		Lib.init(appPath);

		//--- convert args into a list

		List<String> al = new ArrayList<String>();

		for (String arg : args)
			al.add(arg);

		String command = al.get(0);
		al.remove(0);

		if (command.equals("-setup"))
			new Setup().exec(appPath, al);

		if (command.equals("-createdb"))
			new CreateDB().exec(appPath, al);

		else
			System.out.println("Unknown command : "+ command);
	}
}

//==============================================================================

