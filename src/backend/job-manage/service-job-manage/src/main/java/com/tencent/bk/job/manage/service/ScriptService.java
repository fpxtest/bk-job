/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.manage.service;

import com.tencent.bk.job.common.model.PageData;
import com.tencent.bk.job.manage.model.dto.ScriptBasicDTO;
import com.tencent.bk.job.manage.model.dto.ScriptDTO;
import com.tencent.bk.job.manage.model.dto.SyncScriptResultDTO;
import com.tencent.bk.job.manage.model.dto.TagDTO;
import com.tencent.bk.job.manage.model.dto.TemplateStepIDDTO;
import com.tencent.bk.job.manage.model.query.ScriptQuery;
import com.tencent.bk.job.manage.model.web.vo.TagCountVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 公共脚本服务
 */
public interface ScriptService {

    /**
     * 分页查询脚本列表
     *
     * @param scriptQuery 查询条件
     * @return 分页脚本列表
     */
    PageData<ScriptDTO> listPageScript(ScriptQuery scriptQuery);

    /**
     * 根据条件查询脚本
     *
     * @param scriptQuery 查询条件
     * @return 脚本列表
     */
    List<ScriptDTO> listScripts(ScriptQuery scriptQuery);

    /**
     * 根据scriptId查询脚本
     *
     * @param appId    业务ID
     * @param scriptId 脚本ID
     * @return 脚本
     */
    ScriptDTO getScript(Long appId, String scriptId);

    /**
     * 查询脚本
     *
     * @param username 用户账号
     * @param appId    业务ID
     * @param scriptId 脚本ID
     * @return 脚本
     */
    ScriptDTO getScript(String username, Long appId, String scriptId);

    /**
     * 根据scriptIds批量查询脚本基础信息
     *
     * @param scriptIds 脚本ID集合
     * @return 脚本
     */
    List<ScriptBasicDTO> listScriptBasicInfoByScriptIds(Collection<String> scriptIds);

    /**
     * 创建脚本
     *
     * @param username 用户账号
     * @param script   脚本信息
     */
    ScriptDTO createScript(String username, ScriptDTO script);

    /**
     * 删除脚本
     *
     * @param username 用户账号
     * @param appId    业务ID
     * @param scriptId 脚本ID
     */
    void deleteScript(String username, Long appId, String scriptId);

    /**
     * 根据版本ID查询脚本版本
     *
     * @param appId           业务ID
     * @param scriptVersionId 脚本ID
     * @return 脚本版本
     */
    ScriptDTO getScriptVersion(long appId, Long scriptVersionId);

    /**
     * 根据版本ID查询脚本版本
     *
     * @param scriptVersionId 脚本ID
     * @return 脚本版本
     */
    ScriptDTO getScriptVersion(Long scriptVersionId);

    /**
     * 查询脚本版本
     *
     * @param username        用户账号
     * @param appId           业务ID
     * @param scriptVersionId 脚本ID
     * @return 脚本版本
     */
    ScriptDTO getScriptVersion(String username, long appId, Long scriptVersionId);

    /**
     * 根据脚本ID查询所有版本的脚本
     *
     * @param appId    业务ID
     * @param scriptId 脚本ID
     * @return 脚本版本列表
     */
    List<ScriptDTO> listScriptVersion(long appId, String scriptId);

    /**
     * 创建脚本版本
     *
     * @param username      用户账号
     * @param scriptVersion 脚本版本
     */
    ScriptDTO createScriptVersion(String username, ScriptDTO scriptVersion);

    /**
     * 更新脚本版本
     *
     * @param username      用户账号
     * @param scriptVersion 脚本版本
     */
    ScriptDTO updateScriptVersion(String username, ScriptDTO scriptVersion);

    /**
     * 删除脚本版本
     *
     * @param username        操作者
     * @param appId           业务ID
     * @param scriptVersionId 脚本版本ID
     */
    void deleteScriptVersion(String username, Long appId, Long scriptVersionId);

    /**
     * 上线脚本
     *
     * @param appId           业务ID
     * @param username        操作者
     * @param scriptId        脚本ID
     * @param scriptVersionId 脚本版本ID
     */
    void publishScript(Long appId, String username, String scriptId, Long scriptVersionId);

    /**
     * 下线脚本
     *
     * @param appId           业务ID
     * @param username        操作者
     * @param scriptId        脚本ID
     * @param scriptVersionId 脚本版本ID
     */
    void disableScript(Long appId, String username, String scriptId, Long scriptVersionId);

    /**
     * 批量获取脚本的在线版本
     *
     * @param scriptIdList 脚本ID列表
     * @return 脚本信息
     */
    Map<String, ScriptDTO> batchGetOnlineScriptVersionByScriptIds(List<String> scriptIdList);

    /**
     * 更新脚本描述
     *
     * @param appId    业务ID
     * @param username 操作者
     * @param scriptId 脚本ID
     * @param desc     脚本描述
     */
    ScriptDTO updateScriptDesc(Long appId, String username, String scriptId, String desc);

    /**
     * 更新脚本名称
     *
     * @param appId    业务ID
     * @param username 操作者
     * @param scriptId 脚本ID
     * @param newName  脚本名称
     */
    ScriptDTO updateScriptName(Long appId, String username, String scriptId, String newName);

    /**
     * 更新脚本标签
     *
     * @param appId    业务ID
     * @param username 操作者
     * @param scriptId 脚本ID
     * @param tags     脚本标签列表
     */
    ScriptDTO updateScriptTags(Long appId, String username, String scriptId, List<TagDTO> tags);

    /**
     * 根据脚本名称模糊查询业务下的脚本名
     *
     * @param appId   业务ID
     * @param keyword 关键字
     * @return 脚本名称列表
     */
    List<String> listScriptNames(Long appId, String keyword);

    /**
     * 获取业务下的已上线脚本列表
     *
     * @param operator 操作者
     * @param appId    业务ID
     * @return 脚本列表
     */
    List<ScriptDTO> listOnlineScript(String operator, long appId);

    /**
     * 获取脚本已上线脚本版本
     *
     * @param username 用户账号
     * @param appId    业务 ID
     * @param scriptId 脚本 ID
     * @return 已上线版本，如果没有返回null
     */
    ScriptDTO getOnlineScriptVersionByScriptId(String username, long appId, String scriptId);

    /**
     * 分页查询脚本版本列表
     *
     * @param scriptQuery 查询条件
     * @return 脚本版本列表
     */
    PageData<ScriptDTO> listPageScriptVersion(ScriptQuery scriptQuery);

    /**
     * 批量同步脚本到作业模板
     *
     * @param username            用户名
     * @param appId               业务ID
     * @param scriptId            脚本ID
     * @param syncScriptVersionId 需要同步的脚本版本ID
     * @param templateStepIDs     作业模板与步骤信息
     * @return 同步结果
     */
    List<SyncScriptResultDTO> syncScriptToTaskTemplate(String username, Long appId, String scriptId,
                                                       Long syncScriptVersionId,
                                                       List<TemplateStepIDDTO> templateStepIDs);


    /**
     * 是否存在任意业务脚本
     */
    boolean isExistAnyAppScript(long appId);

    /**
     * 获取标签关联的模版数量
     *
     * @param appId 业务 ID
     * @return 标签模版数量
     */
    TagCountVO getTagScriptCount(Long appId);

    /**
     * 根据脚本ID/版本号查询脚本
     *
     * @param username 用户账号
     * @param appId    业务ID
     * @param scriptId 脚本ID
     * @param version  脚本版本
     * @return 脚本版本
     */
    ScriptDTO getByScriptIdAndVersion(String username, Long appId, String scriptId, String version);

    /**
     * 根据脚本ID获取业务脚本
     *
     * @param scriptId 脚本ID
     * @return 脚本
     */
    ScriptDTO getScriptByScriptId(String scriptId);

}
