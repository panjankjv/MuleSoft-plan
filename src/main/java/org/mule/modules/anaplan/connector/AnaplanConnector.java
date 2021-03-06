/**
 * Copyright 2015 Anaplan Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License.md file for the specific language governing permissions and
 * limitations under the License.
 */

package org.mule.modules.anaplan.connector;

import org.mule.api.ConnectionException;
import org.mule.api.ConnectionExceptionCode;
import org.mule.api.annotations.Connect;
import org.mule.api.annotations.ConnectionIdentifier;
import org.mule.api.annotations.Connector;
import org.mule.api.annotations.Disconnect;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.ValidateConnection;
import org.mule.api.annotations.display.Password;
import org.mule.api.annotations.param.ConnectionKey;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.api.annotations.param.Payload;
import org.mule.modules.anaplan.connector.exceptions.AnaplanConnectionException;
import org.mule.modules.anaplan.connector.exceptions.AnaplanOperationException;
import org.mule.modules.anaplan.connector.utils.AnaplanExecuteAction;
import org.mule.modules.anaplan.connector.utils.AnaplanExportOperation;
import org.mule.modules.anaplan.connector.utils.AnaplanImportOperation;
import org.mule.modules.anaplan.connector.utils.LogUtil;

import com.anaplan.client.Service;


/**
 * Anaplan Connector built using Anypoint Studio to export, upsert, delete of
 * data within models. It also supports running generic actions for performing
 * M2M import operations.
 *
 * @author MuleSoft, Inc.
 * @author Spondon Saha.
 */
@Connector(name = "anaplan", schemaVersion = "1.0", friendlyName = "Anaplan")
public class AnaplanConnector {

	private AnaplanConnection apiConn;
	private static AnaplanExportOperation exporter;
	private static AnaplanImportOperation importer;
	private static AnaplanExecuteAction runner;


	/**
	 * Reads in CSV data that represents an Anaplan model, delimited by the
	 * provided delimiter, parses it, then loads it into an Anaplan model.
	 *
	 * @param data
	 * @param anaplanWorkspaceId
	 * @param anaplanModelId
	 * @param anaplanImportId
	 * @param delimiter
	 * @throws AnaplanConnectionException
	 * @throws AnaplanOperationException
	 */
	@Processor(friendlyName = "Import")
	public void importToModel(@Payload String data,
							  String anaplanWorkspaceNameOrId,
							  String anaplanModelNameOrId,
							  String anaplanImportNameOrId,
							  @Default("\t") String delimiter)
									  throws AnaplanConnectionException,
										     AnaplanOperationException {
		// validate API connection
		validateConnection();

		// start the import
		importer = new AnaplanImportOperation(apiConn);
		importer.runImport(data, anaplanWorkspaceNameOrId, anaplanModelNameOrId,
				anaplanImportNameOrId, delimiter);
	}

	/**
	 * Run an export of an Anaplan Model specified by workspace-ID, model-ID and
	 * the export-ID. At the end of each export, the connection is dropped,
	 * hence a check needs to be made to verify if the current connection
	 * exists. If not, re-establish it by calling .openConnection().
	 *
	 * @return CSV string.
	 * @throws AnaplanConnectionException
	 */
	@Processor(friendlyName = "Export")
	public String exportFromModel(String anaplanWorkspaceNameOrId,
								  String anaplanModelNameOrId,
								  String anaplanExportActionNameOrId)
										  throws AnaplanConnectionException,
										  		 AnaplanOperationException {
		// validate API connection
		validateConnection();

		// start the export
		exporter = new AnaplanExportOperation(apiConn);
		return exporter.runExport(anaplanWorkspaceNameOrId, anaplanModelNameOrId,
				anaplanExportActionNameOrId);
	}

	/**
	 * Deletes data from a model by executing the respective delete action.
	 *
	 * @throws AnaplanConnectionException
	 * @throws AnaplanOperationException
	 */
	@Processor(friendlyName = "Execute Action")
	public void executeAction(String anaplanWorkspaceNameOrId,
							  String anaplanModelNameOrId,
							  String anaplanActionId)
										throws AnaplanConnectionException,
											   AnaplanOperationException {
		// validate the API connection
		validateConnection();

		// start the delete process
		runner = new AnaplanExecuteAction(apiConn);
		runner.runExecute(anaplanWorkspaceNameOrId, anaplanModelNameOrId,
						 anaplanActionId);
	}

	/**
	 * Checks if the API connection has been established. If no connection
	 * object was found, then throws {@link AnaplanConnectionException}, else
	 * opens the connection and registers the service object.
	 *
	 * @throws AnaplanConnectionException
	 */
	private void validateConnection() throws AnaplanConnectionException {
		// validate API connection
		if (isConnected()) {
			if (apiConn.getConnection() == null) {
				apiConn.openConnection();
			} else {
				LogUtil.status(apiConn.getLogContext(),
						"Connection to API exists. Proceeding with export...");
			}
		} else {
			throw new AnaplanConnectionException(
					"No connection object: call connect()");
		}
	}

	/**
	 * Connect to the Anaplan API.
	 *
	 * @param username
	 * @param password
	 * @throws ConnectionException
	 */
	@Connect
	public synchronized void connect(
			@ConnectionKey String username,
			@Password String password,
			@Default("https://api.anaplan.com/") String url,
			@Optional @Default("") String proxyHost,
			@Optional @Default("") String proxyUser,
			@Optional @Default("") String proxyPass)
					throws org.mule.api.ConnectionException {

		LogUtil.status(getClass().toString(), "Initiating connection...");
		Service service = null;

		if (apiConn == null) {
			apiConn = new AnaplanConnection(username, password, url, proxyHost,
					proxyUser, proxyPass);
			try {
				service = apiConn.openConnection();
			} catch (AnaplanConnectionException e) {
				throw new org.mule.api.ConnectionException(
						ConnectionExceptionCode.INCORRECT_CREDENTIALS, null,
						e.getMessage(), e);
			}

			if (service == null) {
				throw new org.mule.api.ConnectionException(
						ConnectionExceptionCode.UNKNOWN, null, "No service "
						+ "object acquired after opening connection to Anaplan "
						+ "API!", null);
			} else {
				LogUtil.status(getClass().toString(),
						"Successfully connected to Anaplan API!");
			}
		}
	}

	/**
	 * Disconnect
	 */
	@Disconnect
	public void disconnect() {
		if (apiConn != null) {
			apiConn.closeConnection();
		} else {
			LogUtil.error(getClass().toString(), "No connection to disconnect!");
		}
	}

	/**
	 * Are we connected?
	 */
	@ValidateConnection
	public boolean isConnected() {
		if (apiConn != null) {
			return true;
		}
		return false;
	}

	/**
	 * Are we connected?
	 */
	@ConnectionIdentifier
	public String connectionId() {
		if (apiConn != null)
			return apiConn.getConnectionId();
		else
			return "Not connected!";
	}
}
