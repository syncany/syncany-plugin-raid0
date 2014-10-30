/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.plugins.raid0;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.syncany.config.Config;
import org.syncany.plugins.Plugins;
import org.syncany.plugins.transfer.AbstractTransferManager;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferSettings;
import org.syncany.plugins.transfer.files.MultichunkRemoteFile;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.TempRemoteFile;

/**
 * Transfer manager for the {@link Raid0Plugin}. 
 * 
 * <p>Based on the two {@link TransferSettings} objects held inside of
 * {@link Raid0TransferSettings}, this transfer manager creates two other
 * transfer managers and delegates request either to one or the other.
 * 
 * <p>All metadata requests are delegates to transfer manager 1, while
 * only upload/download/delete/move requests for {@link MultichunkRemoteFile}s
 * are evenly either delegates to transfer manager 1 or 2.
 * 
 * <p>The decision which backend storage to use depends on the (randomly chosen)
 * multichunk identifier. Multichunks with even identifiers are stored on storage 1,
 * multichunks with odd identifiers are stored on storage 2. 
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Raid0TransferManager extends AbstractTransferManager {
	private TransferManager transferManager1;
	private TransferManager transferManager2;
	
	public Raid0TransferManager(Raid0TransferSettings settings, Config config) {
		super(settings, config);
		
		try {
			this.transferManager1 = createTransferManager(settings.getTransferSettings1(), config);
			this.transferManager2 = createTransferManager(settings.getTransferSettings2(), config);
		}
		catch (StorageException e) {
			throw new RuntimeException("Cannot create RAID0 transfer manager.", e);
		} 
	}
	
	@Override
	public void connect() throws StorageException {
		transferManager1.connect();
		transferManager2.connect();
	}

	@Override
	public void disconnect() throws StorageException {
		transferManager1.disconnect();
		transferManager2.disconnect();
	}

	@Override
	public void init(boolean createIfRequired) throws StorageException {
		transferManager1.init(createIfRequired);
		transferManager2.init(createIfRequired);
	}

	/**
	 * Download a file from the remote storage. This method uses the {@link #mapTransferManager(RemoteFile)}
	 * method to decide where to download the file from.  
	 */
	@Override
	public void download(RemoteFile remoteFile, File localFile) throws StorageException {
		mapTransferManager(remoteFile).download(remoteFile, localFile);
	}

	/**
	 * Upload a file to the remote storage. This method uses the {@link #mapTransferManager(RemoteFile)}
	 * method to decide where to upload the file to.  
	 */
	@Override
	public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
		mapTransferManager(remoteFile).upload(localFile, remoteFile);
	}

	/**
	 * Moves a file on the remote storage. This method uses the {@link #mapTransferManager(RemoteFile)}
	 * method to decide where to move the file.  
	 */
	@Override
	public void move(RemoteFile sourceFile, RemoteFile targetFile) throws StorageException {
		mapTransferManager(sourceFile).move(sourceFile, targetFile);
	}

	/**
	 * Deletes a file from the remote storage. This method uses the {@link #mapTransferManager(RemoteFile)}
	 * method to decide where to delete the file from.  
	 */
	@Override
	public boolean delete(RemoteFile remoteFile) throws StorageException {
		return mapTransferManager(remoteFile).delete(remoteFile);
	}

	/**
	 * Retrieves a list of all files in the remote repository, filtered by the type of the
	 * desired file, i.e. by a sub-class of {@link RemoteFile}.
	 * 
	 * <p>For all remote files except {@link MultichunkRemoteFile}, the list is retrieved from storage 1.
	 * For {@link MultichunkRemoteFile}, the file lists from storage 1 and 2 are combined. 
	 */
	@Override
	public <T extends RemoteFile> Map<String, T> list(Class<T> remoteFileClass) throws StorageException {
		if (remoteFileClass == MultichunkRemoteFile.class) {
			Map<String, T> remoteFileList1 = transferManager1.list(remoteFileClass);
			Map<String, T> remoteFileList2 = transferManager2.list(remoteFileClass);
			
			Map<String, T> fullRemoteFileList = new HashMap<>();
			
			fullRemoteFileList.putAll(remoteFileList1);
			fullRemoteFileList.putAll(remoteFileList2);
			
			return fullRemoteFileList;
		}
		else {
			return transferManager1.list(remoteFileClass);
		}		
	}

	/**
	 * Tests whether the target on both storage backends exists. 
	 */
	@Override
	public boolean testTargetExists() throws StorageException {
		return transferManager1.testTargetExists() && transferManager2.testTargetExists();
	}

	/**
	 * Tests whether the target on both storage backends can be written to. 
	 */
	@Override
	public boolean testTargetCanWrite() throws StorageException {
		return transferManager1.testTargetCanWrite() && transferManager2.testTargetCanWrite();
	}

	/**
	 * Tests whether the target on both storage backends can be created. 
	 */
	@Override
	public boolean testTargetCanCreate() throws StorageException {
		return transferManager1.testTargetCanCreate() && transferManager2.testTargetCanCreate();
	}

	/**
	 * Tests whether the repo file exists on the <em>first (!)</em> storage.
	 * The repo file (like all other metadata) is only stored on storage 1.
	 */
	@Override
	public boolean testRepoFileExists() throws StorageException {
		return transferManager1.testRepoFileExists(); // Repo file is on storage 1 only!
	}	
	
	/**
	 * Creates a transfer manager based on the given transfer settings and
	 * the configuration object.
	 */
	private TransferManager createTransferManager(TransferSettings transferSettings, Config config) throws StorageException {
		TransferPlugin transferPlugin = (TransferPlugin) Plugins.get(transferSettings.getType());
		return transferPlugin.createTransferManager(transferSettings, config);
	}
	
	/**
	 * Maps a remote file to either storage 1 or storage 2, based on the type of
	 * the file. 
	 * 
	 * <p>Except for {@link TempRemoteFile}s and {@link MultichunkRemoteFile}s,
	 * all files are mapped to storage 1. Files inside a transaction, aka {@link TempRemoteFile}s,
	 * are also mapped to storage 1, if their target remote file is not a {@link MultichunkRemoteFile}.
	 * 
	 * <p>If the {@link TempRemoteFile}'s target remote file is a {@link MultichunkRemoteFile},
	 * or if the remote file itself is a {@link MultichunkRemoteFile}, the mapping is delegated
	 * to {@link #mapTransferManager(MultichunkRemoteFile)}.
	 */
	private TransferManager mapTransferManager(RemoteFile remoteFile) {
		if (remoteFile.getClass().equals(TempRemoteFile.class)) {
			TempRemoteFile tempRemoteFile = (TempRemoteFile) remoteFile;
			RemoteFile targetRemoteFile = tempRemoteFile.getTargetRemoteFile();
			
			if (targetRemoteFile != null && targetRemoteFile.getClass().equals(MultichunkRemoteFile.class)) {
				return mapTransferManager((MultichunkRemoteFile) targetRemoteFile);
			}
			else {
				return transferManager1;
			}			
		}
		else if (remoteFile.getClass().equals(MultichunkRemoteFile.class)) {
			return mapTransferManager((MultichunkRemoteFile) remoteFile);
		}
		else {
			return transferManager1;
		}
	}
	
	/**
	 * Maps a {@link MultichunkRemoteFile} to either transfer manager 1 or transfer manager 2,
	 * depending on the multichunk identifier. Multichunks with even identifiers are stored
	 * on storage 1, multichunks with odd identifiers are stored on storage 2. 
	 */
	private TransferManager mapTransferManager(MultichunkRemoteFile multiChunk) {
		byte[] multiChunkId = multiChunk.getMultiChunkId();		
		return (multiChunkId[multiChunkId.length-1] % 2 == 0) ? transferManager1 : transferManager2;
	}
}
