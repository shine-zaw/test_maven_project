package com.restapijava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sap.conn.jco.JCoContext;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;

@Path("/")
public class BapiCall {

	@POST
	@Path("postAccountDocument")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public Response postAccDoc(InputStream incomingData) throws JCoException, JSONException, IOException {

		Boolean isError = false;

		// Get Json Data
		StringBuilder stBu = new StringBuilder();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(incomingData));
			String line = null;
			while ((line = in.readLine()) != null) {
				stBu.append(line);
			}
		} catch (Exception e) {
			return Response.status(400).entity("Json String Builder Error!").build();
		}

		JSONObject jsonObject = new JSONObject(stBu.toString());

		// Run BAPI
		JCoDestination destination = JCoDestinationManager.getDestination(jsonObject.get("Des_ID").toString());

		JCoContext.begin(destination);
		JCoFunction funAccDoc = destination.getRepository().getFunction("BAPI_ACC_DOCUMENT_POST");
		JCoFunction funCommit = destination.getRepository().getFunction("BAPI_TRANSACTION_COMMIT");

		if (funAccDoc == null)
			throw new RuntimeException("BAPI_ACC_DOCUMENT_POST not found in SAP.");

		JCoStructure docHeader = funAccDoc.getImportParameterList().getStructure("DOCUMENTHEADER");
		docHeader.setValue("OBJ_TYPE", jsonObject.get("obj_type").toString());
		docHeader.setValue("USERNAME", jsonObject.get("username").toString());
		docHeader.setValue("COMP_CODE", jsonObject.get("comp_code").toString());
		docHeader.setValue("DOC_DATE", jsonObject.get("doc_date").toString());
		docHeader.setValue("PSTNG_DATE", jsonObject.get("pstng_date").toString());
		docHeader.setValue("DOC_TYPE", jsonObject.get("doc_type").toString());

		// Acount GL Item Data
		JCoTable accGL = funAccDoc.getTableParameterList().getTable("ACCOUNTGL");
		JSONArray accGLarry = jsonObject.getJSONArray("AccGL");
		for (int i = 0; i < accGLarry.length(); i++) {
			JSONObject jsonObjectItem = accGLarry.getJSONObject(i);

			accGL.appendRow();
			accGL.setValue("ITEMNO_ACC", Integer.parseInt(jsonObjectItem.get("itemno_acc").toString()));
			accGL.setValue("GL_ACCOUNT", jsonObjectItem.get("gl_account").toString());
			accGL.setValue("VALUE_DATE", jsonObjectItem.get("value_date").toString());
		}

		// Currency Amount Item Data
		JCoTable currAmt = funAccDoc.getTableParameterList().getTable("CURRENCYAMOUNT");
		JSONArray currAmtarry = jsonObject.getJSONArray("CurrAmt");
		for (int i = 0; i < currAmtarry.length(); i++) {
			JSONObject jsonObjectItem = currAmtarry.getJSONObject(i);

			currAmt.appendRow();
			currAmt.setValue("ITEMNO_ACC", Integer.parseInt(jsonObjectItem.get("itemno_acc").toString()));
			currAmt.setValue("AMT_DOCCUR", jsonObjectItem.get("amt_doccur").toString());
			currAmt.setValue("CURRENCY", jsonObjectItem.get("currency").toString());
		}

		funAccDoc.execute(destination);

		// Export parameter
		String OBJ_TYPE = funAccDoc.getExportParameterList().getString("OBJ_TYPE").toString();
		String OBJ_KEY = funAccDoc.getExportParameterList().getString("OBJ_KEY").toString();
		String OBJ_SYS = funAccDoc.getExportParameterList().getString("OBJ_SYS").toString();

		String output = "OBJ_TYPE: " + OBJ_TYPE + "\nOBJ_KEY: " + OBJ_KEY + "\nOBJ_SYS: " + OBJ_SYS;

		// Return Message parameter
		JCoTable returnTbl = funAccDoc.getTableParameterList().getTable("RETURN");
		for (int i = 0; i < returnTbl.getNumRows(); i++) {
			returnTbl.setRow(i);
			if (returnTbl.getString("TYPE").toString().equals("E")) {
				isError = true;
			}

			output = output + "\nMESSAGE" + String.valueOf(i + 1) + ": " + returnTbl.getString("MESSAGE").toString();
		}

		if (isError == false) {
			funCommit.execute(destination);
		}
		JCoContext.end(destination);

		return Response.status(201).entity(output).build();
	}
}