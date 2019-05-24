package com.pig4cloud.pigx.common.poi.excel.export;

import com.pig4cloud.pigx.common.poi.excel.annotation.ExcelTarget;
import com.pig4cloud.pigx.common.poi.excel.entity.ExportParams;
import com.pig4cloud.pigx.common.poi.excel.entity.enmus.ExcelType;
import com.pig4cloud.pigx.common.poi.excel.entity.params.ExcelExportEntity;
import com.pig4cloud.pigx.common.poi.excel.entity.vo.PoiBaseConstants;
import com.pig4cloud.pigx.common.poi.excel.export.base.ExcelExportBase;
import com.pig4cloud.pigx.common.poi.excel.export.styler.IExcelExportStyler;
import com.pig4cloud.pigx.common.poi.exception.excel.ExcelExportException;
import com.pig4cloud.pigx.common.poi.exception.excel.enums.ExcelExportEnum;
import com.pig4cloud.pigx.common.poi.util.PoiPublicUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author: Luckly
 * @date: 2019-05-20
 * <p>
 * Excel导出服务
 */
@Slf4j
public class ExcelExportServer extends ExcelExportBase {

	// 最大行数,超过自动多Sheet
	private int MAX_NUM = 60000;

	private int createHeaderAndTitle(ExportParams entity, Sheet sheet, Workbook workbook, List<ExcelExportEntity> excelParams) {
		int rows = 0, feildWidth = getFieldWidth(excelParams);
		if (entity.getTitle() != null) {
			rows += createHeaderRow(entity, sheet, workbook, feildWidth);
		}
		rows += createTitleRow(entity, sheet, workbook, rows, excelParams);
		sheet.createFreezePane(0, rows, 0, rows);
		return rows;
	}

	/**
	 * 创建 表头改变
	 * 
	 * @param entity
	 * @param sheet
	 * @param workbook
	 * @param feildWidth
	 */
	public int createHeaderRow(ExportParams entity, Sheet sheet, Workbook workbook, int feildWidth) {
		Row row = sheet.createRow(0);
		row.setHeight(entity.getTitleHeight());
		createStringCell(row, 0, entity.getTitle(), getExcelExportStyler().getHeaderStyle(entity.getHeaderColor()), null);
		for (int i = 1; i <= feildWidth; i++) {
			createStringCell(row, i, "", getExcelExportStyler().getHeaderStyle(entity.getHeaderColor()), null);
		}
		sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, feildWidth));
		if (entity.getSecondTitle() != null) {
			row = sheet.createRow(1);
			row.setHeight(entity.getSecondTitleHeight());
			CellStyle style = workbook.createCellStyle();
			style.setAlignment(CellStyle.ALIGN_RIGHT);
			createStringCell(row, 0, entity.getSecondTitle(), style, null);
			for (int i = 1; i <= feildWidth; i++) {
				createStringCell(row, i, "", getExcelExportStyler().getHeaderStyle(entity.getHeaderColor()), null);
			}
			sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, feildWidth));
			return 2;
		}
		return 1;
	}

	public void createSheet(Workbook workbook, ExportParams entity, Class<?> pojoClass, Collection<?> dataSet) {
		if (log.isDebugEnabled()) {
			log.debug("Excel export start ,class is {}", pojoClass);
			log.debug("Excel version is {}", entity.getType().equals(ExcelType.HSSF) ? "03" : "07");
		}
		if (workbook == null || entity == null || pojoClass == null || dataSet == null) {
			throw new ExcelExportException(ExcelExportEnum.PARAMETER_ERROR);
		}
		super.type = entity.getType();
		if (type.equals(ExcelType.XSSF)) {
			MAX_NUM = 1000000;
		}
		Sheet sheet = null;
		try {
			sheet = workbook.createSheet(entity.getSheetName());
		} catch (Exception e) {
			// 重复遍历,出现了重名现象,创建非指定的名称Sheet
			sheet = workbook.createSheet();
		}
		try {
			dataHanlder = entity.getDataHanlder();
			if (dataHanlder != null) {
				needHanlderList = Arrays.asList(dataHanlder.getNeedHandlerFields());
			}
			// 创建表格样式 TODO 出错
			setExcelExportStyler((IExcelExportStyler) entity.getStyle().getConstructor(Workbook.class).newInstance(workbook));
			Drawing patriarch = sheet.createDrawingPatriarch();
			List<ExcelExportEntity> excelParams = new ArrayList<ExcelExportEntity>();
			if (entity.isAddIndex()) {
				excelParams.add(indexExcelEntity(entity));
			}
			// 得到所有字段
			Field fileds[] = PoiPublicUtil.getClassFields(pojoClass);
			ExcelTarget etarget = pojoClass.getAnnotation(ExcelTarget.class);
			String targetId = etarget == null ? null : etarget.value();
			getAllExcelField(entity.getExclusions(), targetId, fileds, excelParams, pojoClass, null);
			sortAllParams(excelParams);
			int index = entity.isCreateHeadRows() ? createHeaderAndTitle(entity, sheet, workbook, excelParams) : 0;
			int titleHeight = index;
			setCellWith(excelParams, sheet);
			short rowHeight = getRowHeight(excelParams);
			setCurrentIndex(1);
			Iterator<?> its = dataSet.iterator();
			List<Object> tempList = new ArrayList<Object>();
			while (its.hasNext()) {
				Object t = its.next();
				index += createCells(patriarch, index, t, excelParams, sheet, workbook, rowHeight);
				tempList.add(t);
				if (index >= MAX_NUM)
					break;
			}
			mergeCells(sheet, excelParams, titleHeight);

			if (entity.getFreezeCol() != 0) {
				sheet.createFreezePane(entity.getFreezeCol(), 0, entity.getFreezeCol(), 0);
			}

			its = dataSet.iterator();
			for (int i = 0, le = tempList.size(); i < le; i++) {
				its.next();
				its.remove();
			}
			// 创建合计信息
			addStatisticsRow(getExcelExportStyler().getStyles(true, null), sheet);

			// 发现还有剩余list 继续循环创建Sheet
			if (dataSet.size() > 0) {
				createSheet(workbook, entity, pojoClass, dataSet);
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			log.error(e.getMessage(), e);
			throw new ExcelExportException(ExcelExportEnum.EXPORT_ERROR, e.getCause());
		}
	}

	public void createSheetForMap(Workbook workbook, ExportParams entity, List<ExcelExportEntity> entityList, Collection<? extends Map<?, ?>> dataSet) {
		if (log.isDebugEnabled()) {
			log.debug("Excel version is {}", entity.getType().equals(ExcelType.HSSF) ? "03" : "07");
		}
		if (workbook == null || entity == null || entityList == null || dataSet == null) {
			throw new ExcelExportException(ExcelExportEnum.PARAMETER_ERROR);
		}
		super.type = entity.getType();
		if (type.equals(ExcelType.XSSF)) {
			MAX_NUM = 1000000;
		}
		Sheet sheet = null;
		try {
			sheet = workbook.createSheet(entity.getSheetName());
		} catch (Exception e) {
			// 重复遍历,出现了重名现象,创建非指定的名称Sheet
			sheet = workbook.createSheet();
		}
		try {
			dataHanlder = entity.getDataHanlder();
			if (dataHanlder != null) {
				needHanlderList = Arrays.asList(dataHanlder.getNeedHandlerFields());
			}
			// 创建表格样式
			setExcelExportStyler((IExcelExportStyler) entity.getStyle().getConstructor(Workbook.class).newInstance(workbook));
			Drawing patriarch = sheet.createDrawingPatriarch();
			List<ExcelExportEntity> excelParams = new ArrayList<ExcelExportEntity>();
			if (entity.isAddIndex()) {
				excelParams.add(indexExcelEntity(entity));
			}
			excelParams.addAll(entityList);
			sortAllParams(excelParams);
			int index = entity.isCreateHeadRows() ? createHeaderAndTitle(entity, sheet, workbook, excelParams) : 0;
			int titleHeight = index;
			setCellWith(excelParams, sheet);
			short rowHeight = getRowHeight(excelParams);
			setCurrentIndex(1);
			Iterator<?> its = dataSet.iterator();
			List<Object> tempList = new ArrayList<Object>();
			while (its.hasNext()) {
				Object t = its.next();
				index += createCells(patriarch, index, t, excelParams, sheet, workbook, rowHeight);
				tempList.add(t);
				if (index >= MAX_NUM)
					break;
			}
			if (entity.getFreezeCol() != 0) {
				sheet.createFreezePane(entity.getFreezeCol(), 0, entity.getFreezeCol(), 0);
			}

			mergeCells(sheet, excelParams, titleHeight);

			its = dataSet.iterator();
			for (int i = 0, le = tempList.size(); i < le; i++) {
				its.next();
				its.remove();
			}
			// 发现还有剩余list 继续循环创建Sheet
			if (dataSet.size() > 0) {
				createSheetForMap(workbook, entity, entityList, dataSet);
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ExcelExportException(ExcelExportEnum.EXPORT_ERROR, e.getCause());
		}
	}

	/**
	 * 创建表头
	 * 
	 * @param title
	 * @param index
	 */
	private int createTitleRow(ExportParams title, Sheet sheet, Workbook workbook, int index, List<ExcelExportEntity> excelParams) {
		Row row = sheet.createRow(index);
		int rows = getRowNums(excelParams);
		row.setHeight((short) 450);
		Row listRow = null;
		if (rows == 2) {
			listRow = sheet.createRow(index + 1);
			listRow.setHeight((short) 450);
		}
		int cellIndex = 0;
		CellStyle titleStyle = getExcelExportStyler().getTitleStyle(title.getColor());
		for (int i = 0, exportFieldTitleSize = excelParams.size(); i < exportFieldTitleSize; i++) {
			ExcelExportEntity entity = excelParams.get(i);
			if (StringUtils.isNotBlank(entity.getName())) {
				createStringCell(row, cellIndex, entity.getName(), titleStyle, entity);
			}
			if (entity.getList() != null) {
				List<ExcelExportEntity> sTitel = entity.getList();
				if (StringUtils.isNotBlank(entity.getName())) {
					sheet.addMergedRegion(new CellRangeAddress(index, index, cellIndex, cellIndex + sTitel.size() - 1));
				}
				for (int j = 0, size = sTitel.size(); j < size; j++) {
					createStringCell(rows == 2 ? listRow : row, cellIndex, sTitel.get(j).getName(), titleStyle, entity);
					cellIndex++;
				}
				cellIndex--;
			} else if (rows == 2) {
				createStringCell(listRow, cellIndex, "", titleStyle, entity);
				sheet.addMergedRegion(new CellRangeAddress(index, index + 1, cellIndex, cellIndex));
			}
			cellIndex++;
		}
		return rows;

	}

	/**
	 * 判断表头是只有一行还是两行
	 * 
	 * @param excelParams
	 * @return
	 */
	private int getRowNums(List<ExcelExportEntity> excelParams) {
		for (int i = 0; i < excelParams.size(); i++) {
			if (excelParams.get(i).getList() != null && StringUtils.isNotBlank(excelParams.get(i).getName())) {
				return 2;
			}
		}
		return 1;
	}

	private ExcelExportEntity indexExcelEntity(ExportParams entity) {
		ExcelExportEntity exportEntity = new ExcelExportEntity();
		exportEntity.setOrderNum(0);
		exportEntity.setName(entity.getIndexName());
		exportEntity.setWidth(10);
		exportEntity.setFormat(PoiBaseConstants.IS_ADD_INDEX);
		return exportEntity;
	}

}
