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

import org.syncany.config.Config;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * RAID0-like storage {@link TransferPlugin} for Syncany.
 * 
 * <p>The RAID0 plugin evenly splits the multichunk data across two
 * backend storages, thereby allowing for a maximum storage of 2*min(N,M)
 * -- with N and M being the disk space of the two back storages.
 * 
 * <p>The plugin offers no redundancy or additional availability mechanisms.
 * Like in actual RAID0 systems, the plugin only increases disk space.
 * 
 * <p>All metadata is stored on storage 1, only multichunks split evenly 
 * across both storage 1 and 2.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class Raid0Plugin extends TransferPlugin {
	public Raid0Plugin() {
		super("raid0");
	}

	@Override
	public Raid0TransferSettings createEmptySettings() throws StorageException {
		return new Raid0TransferSettings();
	}

	@Override
	public Raid0TransferManager createTransferManager(TransferSettings transferSettings, Config config) throws StorageException {
		return new Raid0TransferManager((Raid0TransferSettings) transferSettings, config);
	}
}
