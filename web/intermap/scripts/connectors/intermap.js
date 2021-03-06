
/* TODO:  adding to every function the "imc_" (InterMap Connector) prefix */

var activeLayerId = null; // active layer

function imc_reloadLayers()
{
	var url = '/intermap/srv/'+Env.lang+'/map.layers.getOrder';
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			onComplete: im_buildLayerList
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                                  Layers                                   *
 *                                                                           *
 *****************************************************************************/

function updateInspector(layerId)
{
	var url = '/intermap/srv/'+Env.lang+'/map.layers.getInspectorData';
	var pars = 'id=' + layerId;
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: updateInspectorControls,
			onFailure: reportError
		}
	);
}

// start ajax transaction to set the layer order
function imc_setLayersOrder(order)
{
	var url = '/intermap/srv/'+Env.lang+'/map.layers.setOrder';
	var pars = order.replace(new RegExp("\\[\\]", "g"), ""); // remove all [ and ] - jeeves doesn't accept in parameter name otherwise
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: refreshNeeded,
			onFailure: reportError
		}
	);
}

function imc_zoomToLayer(layerId)
{
	deleteAoi();
	//unsetAoi();
	$('im_geonetRecords').className = 'hidden';	
	
	setStatus('busy');
	var url = '/intermap/srv/'+Env.lang+'/map.zoomToService';
	var pars = 'id=' + layerId;
	
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			parameters: pars,
			onComplete: refreshNeeded,
			onFailure: reportError
		}
	);
}

function toggleVisibility(id) {
	var url = '/intermap/srv/'+Env.lang+'/map.layers.toggleVisibility';
	var pars = 'id=' + id;
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: function(request) { setLayerVisibility(request, id); },
			onFailure: reportError
		}
	);
}

function showActiveLayerLegend(id) {
    showLegend(activeLayerId);
}
function showLegend(id) {
	window.open('/intermap/srv/'+Env.lang+'/map.service.getLegend?id=' + id, 'dialog', 'HEIGHT=300,WIDTH=400,scrollbars=yes,toolbar=yes,status=yes,menubar=yes,location=yes,resizable=yes');
}


/*****************************************************************************
 *                                                                           *
 *                                Delete layer                               *
 *                                                                           *
 *****************************************************************************/

// start ajax transaction to delete a layer
function imc_deleteLayer(id)
{
	var url = '/intermap/srv/'+Env.lang+'/map.layers.deleteLayer';
	var pars = 'id=' + id ;
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: refreshNeeded,
			onFailure: reportError
		}
	);
}

/*****************************************************************************
 *                                                                           *
 *                                 Add layer                                 *
 *                                                                           *
 *****************************************************************************/

// start ajax transaction to delete a layer
function setAddLayersWindowContent()
{
	var url = '/intermap/srv/'+Env.lang+'/mapServers.listServers';
	
	Position.clone('im_map', 'im_addLayers');
	var myAjax = new Ajax.Updater
	(
		'im_addLayers',
		url, 
		{
			method: 'get',
			onFailure: reportError
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                            Layer transparency                             *
 *                                                                           *
 *****************************************************************************/

function imc_setLayerTransparency(id, transparency)
{
	var url = '/intermap/srv/'+Env.lang+'/map.layers.setTransparency';
	var pars = 'id=' + id + '&transparency=' + transparency / 100.0;
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: refreshNeeded,
			onFailure: reportError
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                    Map operations (zoom, pan, identify)                   *
 *                                                                           *
 *****************************************************************************/

// Starts ajax transaction to perform a map action (zoomin, zoomout)
function imc_mapAction(tool, xmin, ymin, xmax, ymax)
{
	var url = '/intermap/srv/'+Env.lang+'/map.action';
	var pars = 'maptool=' + tool + '&mapimgx=' + xmin + '&mapimgy=' + ymin + '&mapimgx2=' + xmax + '&mapimgy2=' + ymax;
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: updateMapImage,
			onFailure: reportError
		}
	);
}

function imc_minimapAction(tool, xmin, ymin, xmax, ymax, w, h)
{
	var url = '/intermap/srv/'+Env.lang+'/map.action';
	var pars = 'maptool=' + tool + '&mapimgx=' + xmin + '&mapimgy=' + ymin + '&mapimgx2=' + xmax + '&mapimgy2=' + ymax + 
	                 "&width=" + w + "&height="+h +
	                 "&"+im_mm_getURLbbox(); // FIXME: we should pass bb as param 
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: im_mm_updateMapImage,
			onFailure: reportError
		}
	);
}

function imc_mm_move(deltax, deltay, width, height)
{
	var url = '/intermap/srv/'+Env.lang+'/map.move';  
	var pars = 'deltax=' + deltax + '&deltay=' + deltay + 
	                "&width=" + width + "&height=" + height +
	                "&" + im_mm_getURLbbox();	 // FIXME: we need it as func param                
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: im_mm_updateMapImage,
			onFailure: reportError
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                              Area of interest                             *
 *                                                                           *
 *****************************************************************************/

function setAoi(xmin, ymin, xmax, ymax)
{
	var url = '/intermap/srv/'+Env.lang+'/map.setAoi';
	var pars = 'minx=' + xmin + '&miny=' + ymin + '&maxx=' + xmax + '&maxy=' + ymax;
	//alert(pars); // DEBUG
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			//onComplete: showResponse,
			onFailure: reportError
		}
	);
}

function unsetAoi()
{
	var url = '/intermap/srv/'+Env.lang+'/map.unsetAoi';
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			//onComplete: showResponse,
			onFailure: reportError
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                                Map refresh                                *
 *                                                                           *
 *****************************************************************************/

function refreshButtonListener()
{
	setStatus('busy');
	
	var url = '/intermap/srv/'+Env.lang+'/map.update';
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			onComplete: updateMapImage,
			onFailure: reportError
		}
	);
}


/*****************************************************************************
 *                                                                           *
 *                                Full extent                                *
 *                                                                           *
 *****************************************************************************/

function fullExtentButtonListener()
{
	setStatus('busy');
	
	deleteAoi();
	
	if (currentTool == 'zoomout' || currentTool == 'pan') setTool('zoomin');
	
	var url = '/intermap/srv/'+Env.lang+'/map.fullExtent';
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			onComplete: updateMapImage,
			onFailure: reportError
		}
	);
}

function imc_mm_fullExtent(w,h)
{
	im_mm_setStatus('busy');
	
	im_mm_deleteAoi();
	
	if (im_mm_currentTool == 'zoomout' || im_mm_currentTool == 'pan') 
	    im_mm_setTool('zoomin');
	
	var url = '/intermap/srv/'+Env.lang+'/map.fullExtent';
	var pars = "width=" + w + "&height="+h
	
	
	var myAjax = new Ajax.Request (
		url, 
		{
			method: 'get',
			parameters: pars,
			onComplete: im_mm_updateMapImage,
			onFailure: reportError
		}
	);
}



/*****************************************************************************
 *                                                                           *
 *****************************************************************************/


function getGeonetData(xmin, ymin, xmax, ymax, from, to) // DEBUG
{
	var url = '/intermap/srv/'+Env.lang+'/geonet.getGeonetRecords';
	var pars = 'minx=' + xmin + '&miny=' + ymin + '&maxx=' + xmax + '&maxy=' + ymax + '&from=' + from + '&to=' + to;
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			parameters: pars,
			onComplete: presentGeonetResponse,
			onFailure: reportError
		}
	);
}

function addLayer(baseUrl, serviceName) // DEBUG
{
	setStatus('busy');
	
	var url = 'map.layers.add';
	var pars = 'url=' + baseUrl + '&service=' + serviceName;
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			parameters: pars,
			onComplete: layerAdded,
			onFailure: reportError
		}
	);
}





function imc_toggleImageSize()
{
	deleteAoi();
	unsetAoi();
	$('im_geonetRecords').className = 'hidden';	
	
	setStatus('busy');
	var url = '/intermap/srv/'+Env.lang+'/map.toggleImageSize';
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			onComplete: updateMapSize,
			onFailure: reportError
		}
	);
}

function updateMapSize(req)
{
	$('im_mapImg').style.width = req.responseXML.getElementsByTagName('width')[0].firstChild.nodeValue;
	$('im_mapImg').style.height = req.responseXML.getElementsByTagName('height')[0].firstChild.nodeValue;
	$('im_map').style.width = req.responseXML.getElementsByTagName('width')[0].firstChild.nodeValue;
	$('im_map').style.height = req.responseXML.getElementsByTagName('height')[0].firstChild.nodeValue;
	
	refreshButtonListener();
}

function imc_addService(surl, service, type, callback)
{
	var url = '/intermap/srv/'+Env.lang+'/map.addServicesExt';
	
	var pars = 'url=' + surl + '&service=' + service + '&type=' + type;
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			parameters: pars,

			onComplete: callback,
			onFailure: reportError
		}
	);

}

function imc_addServices(surl, serviceArray, type, callback)
{
	var url = '/intermap/srv/'+Env.lang+'/map.addServices';	
	var pars = 'url=' + surl + '&type=' + type;
	
	serviceArray.each(
	    function(service)
	    {
	        pars += '&service=' + service;
	    }
	);
	
	var myAjax = new Ajax.Request (
		url,
		{
			method: 'get',
			parameters: pars,

			onComplete: callback,
			onFailure: reportError
		}
	);
}

function imc_loadMapServers( callback )
{
	var myAjax = new Ajax.Request (
		'/intermap/srv/'+Env.lang+'/mapServers.listServers.xml',
		{
			method: 'get',

			onComplete: callback,
			onFailure: reportError
		}
	);

}

function imc_loadServices( id, callback )
{
	var myAjax = new Ajax.Request (
		'/intermap/srv/'+Env.lang+'/mapServers.getServices.xml',
		{
		           parameters: "mapserver="+id,
			method: 'get',

			onComplete: callback,
			onFailure: reportError
		}
	);

}
