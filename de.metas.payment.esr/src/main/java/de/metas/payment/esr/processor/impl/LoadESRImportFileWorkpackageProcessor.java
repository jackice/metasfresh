/**
 *
 */
package de.metas.payment.esr.processor.impl;

/*
 * #%L
 * de.metas.payment.esr
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.util.List;

import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.ad.trx.api.ITrxRunConfig;
import org.adempiere.ad.trx.api.ITrxRunConfig.OnRunnableFail;
import org.adempiere.ad.trx.api.ITrxRunConfig.OnRunnableSuccess;
import org.adempiere.ad.trx.api.ITrxRunConfig.TrxPropagation;
import org.adempiere.util.Services;

import de.metas.async.api.IQueueDAO;
import de.metas.async.model.I_C_Queue_WorkPackage;
import de.metas.async.spi.IWorkpackageProcessor;
import de.metas.payment.esr.api.IESRImportBL;
import de.metas.payment.esr.model.I_ESR_Import;

/**
 * Import the esr from the file which is stored in attachment
 *
 * @author cg
 *
 */
public class LoadESRImportFileWorkpackageProcessor implements IWorkpackageProcessor
{
	@Override
	public Result processWorkPackage(final I_C_Queue_WorkPackage workpackage, final String localTrxName)
	{
		final IQueueDAO queueDAO = Services.get(IQueueDAO.class);
		final IESRImportBL esrImportBL = Services.get(IESRImportBL.class);

		final List<I_ESR_Import> records = queueDAO.retrieveItems(workpackage, I_ESR_Import.class, localTrxName);

		for (final I_ESR_Import esrImport : records)
		{
			// the esr can not have the file twice; is restricted before in the process so we are not overlapping
			// we can load the file twice
			if (esrImport.isProcessed() || !esrImport.isActive() || esrImport.isValid())
			{
				// already processed => do nothing
				// already imported
				continue;
			}

			//
			esrImportBL.loadESRImportFile(esrImport);

			// import is done, so we can process and create payments
			processESRImportFile(esrImport);
		}

		return Result.SUCCESS;
	}

	private void processESRImportFile(final I_ESR_Import esrImport)
	{
		final ITrxManager trxManager = Services.get(ITrxManager.class);
		final ITrxRunConfig trxRunConfig = trxManager.newTrxRunConfigBuilder()
				.setTrxPropagation(TrxPropagation.NESTED)
				.setOnRunnableSuccess(OnRunnableSuccess.COMMIT)
				.setOnRunnableFail(OnRunnableFail.ASK_RUNNABLE)
				.build();

		Services.get(IESRImportBL.class).process(esrImport, trxRunConfig);

	}
}
