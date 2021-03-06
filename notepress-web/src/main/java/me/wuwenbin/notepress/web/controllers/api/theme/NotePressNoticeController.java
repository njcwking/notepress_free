package me.wuwenbin.notepress.web.controllers.api.theme;


import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.wuwenbin.notepress.api.constants.enums.DictionaryTypeEnum;
import me.wuwenbin.notepress.api.model.NotePressResult;
import me.wuwenbin.notepress.api.model.entity.Content;
import me.wuwenbin.notepress.api.model.entity.Dictionary;
import me.wuwenbin.notepress.api.model.entity.Param;
import me.wuwenbin.notepress.api.model.entity.system.SysNotice;
import me.wuwenbin.notepress.api.model.entity.system.SysUser;
import me.wuwenbin.notepress.api.query.DictionaryQuery;
import me.wuwenbin.notepress.api.service.*;
import me.wuwenbin.notepress.service.facade.MailFacade;
import me.wuwenbin.notepress.web.controllers.api.NotePressBaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.wuwenbin.notepress.api.constants.ParamKeyConstant.SWITCH_COMMENT;
import static me.wuwenbin.notepress.api.constants.ParamKeyConstant.SWITCH_COMMENT_NOTICE_MAIL;

/**
 * created by Wuwenbin on 2018/2/8 at 18:54
 *
 * @author wuwenbin
 */
@Controller
@RequestMapping("/message")
@RequiredArgsConstructor(onConstructor = @__({@Autowired}))
public class NotePressNoticeController extends NotePressBaseController {

    private final IParamService paramService;
    private final IDictionaryService dictionaryService;
    private final ISysNoticeService noticeService;
    private final ISysUserService userService;
    private final IContentService contentService;
    private final MailFacade mailFacade;

    @GetMapping
    public String messagePage(Model model, Page<SysNotice> noticePage) {
        noticePage.setSize(15);
        OrderItem oi = OrderItem.desc("gmt_create");
        noticePage.addOrder(oi);
        //友情链接
        List<Dictionary> linkList = dictionaryService.list(DictionaryQuery.build("dictionary_type", DictionaryTypeEnum.LINK));
        model.addAttribute("linkList", linkList);
        //页面信息
        IPage<SysNotice> sysNoticePage = toPageBeanNull(noticeService.findMessagePage(noticePage, "-1", "留言"));
        model.addAttribute("messagePage", sysNoticePage);
        model.addAttribute("messageUserInfo", messageUserInfo(noticePage));
        List<SysNotice> rankList = toListBeanNull(noticeService.findMessageRankList());
        model.addAttribute("messageRankList", rankList);
        //随机几篇文章
        List<Content> randomContents = toListBeanNull(contentService.findRandomContents(6));
        model.addAttribute("randomContents", randomContents);
        //数量前30的tag
        List<Dictionary> tagListTop30 = toListBeanNull(dictionaryService.top30TagList());
        model.addAttribute("tagList", tagListTop30);
        return "message";
    }

    @PostMapping("/lists")
    @ResponseBody
    public NotePressResult comments(Page<SysNotice> page) {
        page.setSize(20);
        OrderItem oi = OrderItem.desc("gmt_create");
        page.addOrder(oi);
        Page<SysNotice> mpage = toPageBeanNull(noticeService.findMessagePage(page, "-1", "留言"));
        Map<String, Object> res = MapUtil.of("messagePage", mpage);
        assert mpage != null;
        res.put("messageUserInfo", messageUserInfo(mpage));
        return writeJsonOk(res);
    }

    @PostMapping("/token/sub")
    @ResponseBody
    public NotePressResult sub(@Valid SysNotice notice, BindingResult bindingResult) {
        String commentStatus = toRNull(paramService.fetchParamByName(SWITCH_COMMENT), Param.class, Param::getValue);
        if ("1".equals(commentStatus)) {
            if (!bindingResult.hasErrors()) {
                NotePressResult subRes = noticeService.subMessage(notice);
                if (subRes.isSuccess()) {
                    String mailStatus = toRNull(paramService.fetchParamByName(SWITCH_COMMENT_NOTICE_MAIL), Param.class, Param::getValue);
                    if ("1".equals(mailStatus)) {
                        String sendContent = "------<br/>" +
                                "<b>用户</b>：【<span style='color:red;'>{}</span>】 在 <b>{}</b>楼发表了内容：{}</br>" +
                                "------</br>";
                        sendContent = StrUtil.format(sendContent, userService.getById(notice.getUserId()).getUsername(), notice.getFloor(), notice.getCommentHtml());
                        mailFacade.sendNotice(sendContent, basePath(request), notice.getContentId());
                    }
                    if (!StringUtils.isEmpty(notice.getReplyId())) {
                        String email = userService.getById(notice.getReplyId()).getEmail();
                        mailFacade.sendNotice2User(email, notice.getContentId(), basePath(request));
                    }
                    return writeJsonOkMsg("发表评论成功");
                }
                return writeJsonErrorMsg("发表评论失败，" + subRes.getMsg());
            } else {
                return writeJsonJsr303(bindingResult.getFieldErrors());
            }
        } else {
            return writeJsonErrorMsg("未开放评论！");
        }
    }

    //========================私有方法=======================

    private Map<Long, Map<String, Object>> messageUserInfo(Page<SysNotice> page) {
        return page.getRecords().stream().collect(
                Collectors.toMap(
                        SysNotice::getId,
                        sysNotice -> {
                            Map<String, Object> resMap = new HashMap<>(2);
                            SysUser u = userService.getById(sysNotice.getUserId());
                            resMap.put("avatar", u.getAvatar());
                            resMap.put("nickname", u.getNickname());
                            resMap.put("admin", u.getAdmin());
                            resMap.put("email", u.getEmail());
                            return resMap;
                        }
                )
        );
    }
}
