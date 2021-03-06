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

package org.fao.geonet.kernel.harvest.harvester.geonet20;

import java.util.Iterator;
import java.util.List;
import jeeves.interfaces.Logger;
import jeeves.resources.dbms.Dbms;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Util;
import jeeves.utils.XmlRequest;
import org.fao.geonet.constants.Edit;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.harvest.harvester.CategoryMapper;
import org.fao.geonet.kernel.harvest.harvester.GroupMapper;
import org.fao.geonet.kernel.harvest.harvester.UUIDMapper;
import org.fao.geonet.util.ISODate;
import org.jdom.Element;

//=============================================================================

public class Aligner
{
	//--------------------------------------------------------------------------
	//---
	//--- Constructor
	//---
	//--------------------------------------------------------------------------

	public Aligner(Logger log, XmlRequest req, GeonetParams params, DataManager dm,
						Dbms dbms, ServiceContext sc, CategoryMapper cm, GroupMapper gm)
	{
		this.log        = log;
		this.req        = req;
		this.params     = params;
		this.dataMan    = dm;
		this.dbms       = dbms;
		this.context    = sc;
		this.localCateg = cm;
		this.localGroups= gm;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Alignment method
	//---
	//--------------------------------------------------------------------------

	public AlignerResult align(Element result, String siteId) throws Exception
	{
		log.info("Start of alignment for site-id="+ siteId);

		this.result = new AlignerResult();
		this.result.siteId = siteId;

		List mdList = result.getChildren("metadata");

		//-----------------------------------------------------------------------
		//--- retrieve local uuids for given site-id

		localUuids = new UUIDMapper(dbms, siteId);

		//-----------------------------------------------------------------------
		//--- remove old metadata

		for (String uuid : localUuids.getUUIDs())
			if (!exists(mdList, uuid))
			{
				String id = localUuids.getID(uuid);

				log.debug("  - Removing old metadata with id="+ id);
				dataMan.deleteMetadata(dbms, id);
				dbms.commit();
				this.result.locallyRemoved++;
			}

		//-----------------------------------------------------------------------
		//--- insert/update new metadata

		for(Iterator i=mdList.iterator(); i.hasNext(); )
		{
			Element info = ((Element) i.next()).getChild("info", Edit.NAMESPACE);

			String remoteId  = info.getChildText("id");
			String remoteUuid= info.getChildText("uuid");
			String schema    = info.getChildText("schema");
			String changeDate= info.getChildText("changeDate");

			this.result.totalMetadata++;

			log.debug("Obtained remote id="+ remoteId +", changeDate="+ changeDate);

			if (!dataMan.existsSchema(schema))
			{
				log.debug("  - Skipping unsupported schema : "+ schema);
				this.result.schemaSkipped++;
			}
			else
			{
				String id = dataMan.getMetadataId(dbms, remoteUuid);

				if (id == null)	id = addMetadata(siteId, info);
					else				updateMetadata(siteId, info, id);

				dbms.commit();
				dataMan.indexMetadata(dbms, id);
			}
		}

		log.info("End of alignment for site-id="+ siteId);

		return this.result;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Private methods : addMetadata
	//---
	//--------------------------------------------------------------------------

	private String addMetadata(String siteId, Element info) throws Exception
	{
		String remoteId   = info.getChildText("id");
		String remoteUuid = info.getChildText("uuid");
		String schema     = info.getChildText("schema");
		String createDate = info.getChildText("createDate");
		String changeDate = info.getChildText("changeDate");

		log.debug("  - Adding metadata with remote id="+ remoteId);

		Element md = getRemoteMetadata(req, remoteId);

		String id = dataMan.insertMetadataExt(dbms, schema, md, context.getSerialFactory(),
														  siteId, createDate, changeDate, remoteUuid, 1, null);

		int iId = Integer.parseInt(id);

		dataMan.setTemplate(dbms, iId, "n", null);
		dataMan.setHarvested(dbms, iId, siteId);

		result.addedMetadata++;

		addCategories(id, info.getChildren("category"));
		addPrivileges(id, info);

		return id;
	}

	//--------------------------------------------------------------------------
	//--- Categories
	//--------------------------------------------------------------------------

	private void addCategories(String id, List categ) throws Exception
	{
		for(Iterator j=categ.iterator(); j.hasNext();)
		{
			String catName = ((Element) j.next()).getText();
			String catId   = localCateg.getID(catName);

			if (catId != null)
			{
				//--- remote category exists locally

				log.debug("    - Setting category : "+ catName);
				dataMan.setCategory(dbms, id, catId);
			}
		}
	}

	//--------------------------------------------------------------------------
	//--- Privileges
	//--------------------------------------------------------------------------

	private void addPrivileges(String id, Element info) throws Exception
	{
		//--- set view privilege for both groups 'intranet' and 'all'
		dataMan.setOperation(dbms, id, "0", "0");
		dataMan.setOperation(dbms, id, "1", "0");
	}

	//--------------------------------------------------------------------------
	//---
	//--- Private methods : updateMetadata
	//---
	//--------------------------------------------------------------------------

	private void updateMetadata(String siteId, Element info, String id) throws Exception
	{
		String remoteId  = info.getChildText("id");
		String remoteUuid= info.getChildText("uuid");
		String changeDate= info.getChildText("changeDate");

		if (localUuids.getID(remoteUuid) == null)
		{
			log.error("  - Warning! The remote uuid '"+ remoteUuid +"' does not belong to site '"+ siteId+"'");
			log.error("     - The site id of this metadata has been changed.");
			log.error("     - The metadata update will be skipped.");

			result.uuidSkipped++;
		}
		else
		{
			updateMetadata(id, remoteId, remoteUuid, changeDate);
			updateCategories(id, info);
		}
	}

	//--------------------------------------------------------------------------

	private void updateMetadata(String id, String remoteId, String remoteUuid, String changeDate) throws Exception
	{
		String date = localUuids.getChangeDate(remoteUuid);

		if (!updateCondition(date, changeDate))
		{
			log.debug("  - XML not changed to local metadata with id="+ id);
			result.unchangedMetadata++;
		}
		else
		{
			log.debug("  - Updating local metadata with id="+ id);

			Element md = getRemoteMetadata(req, remoteId);

			dataMan.updateMetadataExt(dbms, id, md, changeDate);
			result.updatedMetadata++;
		}
	}

	//--------------------------------------------------------------------------

	private void updateCategories(String id, Element info) throws Exception
	{
		List catList = info.getChildren("category");

		//--- remove old categories

		List locCateg = dataMan.getCategories(dbms, id).getChildren();

		for (int i=0; i<locCateg.size(); i++)
		{
			Element el = (Element) locCateg.get(i);

			String catId   = el.getChildText("id");
			String catName = el.getChildText("name");

			if (!existsCategory(catList, catName))
			{
				log.debug("  - Unsetting category : "+ catName);
				dataMan.unsetCategory(dbms, id, catId);
			}
		}

		//--- add new categories

		for (Iterator j=catList.iterator(); j.hasNext();)
		{
			Element categ   = (Element) j.next();
			String  catName = categ.getAttributeValue("name");
			String  catId   = localCateg.getID(catName);

			if (catId != null)
				//--- it is not necessary to query the db. Anyway...
				if (!dataMan.isCategorySet(dbms, id, catId))
				{
					log.debug("  - Setting category : "+ catName);
					dataMan.setCategory(dbms, id, catId);
				}
		}
	}

	//--------------------------------------------------------------------------

	private boolean existsCategory(List catList, String name)
	{
		for(Iterator i=catList.iterator(); i.hasNext();)
		{
			Element categ   = (Element) i.next();
			String  catName = categ.getText();

			if (catName.equals(name))
				return true;
		}

		return false;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Private methods
	//---
	//--------------------------------------------------------------------------

	private Element getRemoteMetadata(XmlRequest req, String id) throws Exception
	{
		req.setAddress("/"+ params.servlet +"/srv/en/"+ Geonet.Service.XML_METADATA_GET);
		req.clearParams();
		req.addParam("id", id);

		Element md   = req.execute();
		Element info = md.getChild("info", Edit.NAMESPACE);

		if (info != null)
			info.detach();

		return md;
	}

	//--------------------------------------------------------------------------
	/** Return true if the sourceId is present in the remote site */

	private boolean exists(List mdList, String uuid)
	{
		for(Iterator i=mdList.iterator(); i.hasNext(); )
		{
			Element elInfo = ((Element) i.next()).getChild("info", Edit.NAMESPACE);

			if (uuid.equals(elInfo.getChildText("uuid")))
				return true;
		}

		return false;
	}

	//--------------------------------------------------------------------------

	private boolean updateCondition(String localDate, String remoteDate)
	{
		ISODate local = new ISODate(localDate);
		ISODate remote= new ISODate(remoteDate);

		//--- accept if remote date is greater than local date

		return (remote.sub(local) > 0);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Variables
	//---
	//--------------------------------------------------------------------------

	private Dbms           dbms;
	private Logger         log;
	private XmlRequest     req;
	private GeonetParams   params;
	private DataManager    dataMan;
	private ServiceContext context;
	private CategoryMapper localCateg;
	private GroupMapper    localGroups;
	private UUIDMapper     localUuids;
	private AlignerResult  result;
}

//=============================================================================

class AlignerResult
{
	public String siteId;

	public int totalMetadata;
	public int addedMetadata;
	public int updatedMetadata;
	public int unchangedMetadata;
	public int locallyRemoved;
	public int schemaSkipped;
	public int uuidSkipped;
}

//=============================================================================

