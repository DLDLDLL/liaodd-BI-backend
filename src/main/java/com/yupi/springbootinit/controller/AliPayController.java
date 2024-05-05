package com.yupi.springbootinit.controller;

import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.entity.AiFrequency;
import com.yupi.springbootinit.model.entity.AiFrequencyOrder;
import com.yupi.springbootinit.model.entity.AlipayInfo;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.PayOrderEnum;
import com.yupi.springbootinit.model.vo.AlipayInfoVO;
import com.yupi.springbootinit.service.AiFrequencyOrderService;
import com.yupi.springbootinit.service.AiFrequencyService;
import com.yupi.springbootinit.service.AlipayInfoService;
import com.yupi.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@RestController
@Slf4j
@RequestMapping("/alipay")
public class AliPayController {
    @Resource
    private AiFrequencyOrderService aiFrequencyOrderService;

    @Resource
    private AlipayInfoService alipayInfoService;

    @Resource
    private UserService userService;

    @Resource
    private AiFrequencyService aiFrequencyService;

    public static String URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    public static String CHARSET = "UTF-8";
    public static String SIGNTYPE = "RSA2";

    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;

    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;


    /**
     * 生成二维码
     *
     * @param orderId
     * @param request
     * @return
     */
    @PostMapping("/payCode")
    @ResponseBody
    public BaseResponse<AlipayInfoVO> payCode(long orderId, HttpServletRequest request) {
        if (orderId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
//        long alipayAccountNo = alipayInfoService.getPayNo(orderId, loginUser.getId());
//        String url = String.format("http://192.168.32.1:8101/api/alipay/pay?alipayAccountNo=%s", alipayAccountNo);
        // 生成二维码调用支付接口
        String url = String.format("http://8cwxq3.natappfree.cc/api/alipay/pay?orderId=%s&userId=%s", orderId, loginUser.getId());
        String generateQrCode = QrCodeUtil.generateAsBase64(url, new QrConfig(400, 400), "png");
        AlipayInfoVO alipayInfoVO = new AlipayInfoVO();
//        alipayInfoVO.setAlipayAccountNo(String.valueOf(alipayAccountNo));
        alipayInfoVO.setQrCode(generateQrCode);
        alipayInfoVO.setOrderId(orderId);
        return ResultUtils.success(alipayInfoVO);
    }

    /**
     * 支付接口
     *
     * @param response
     * @throws AlipayApiException
     * @throws IOException
     */
    @GetMapping("/pay")
    public void pay(long orderId, long userId, HttpServletResponse response) throws AlipayApiException, IOException {
        // 判断订单状态
        AiFrequencyOrder order = getOrder(orderId);
        Integer orderStatus = order.getOrderStatus();
        if (orderStatus.equals(Integer.valueOf(PayOrderEnum.COMPLETE.getValue()))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "订单已支付，无需重复支付");
        } else if (orderStatus.equals(Integer.valueOf(PayOrderEnum.CANCEL_ORDER.getValue()))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "订单已被取消，无法支付");
        } else if (orderStatus.equals(Integer.valueOf(PayOrderEnum.TIMEOUT_ORDER.getValue()))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "订单已超时，无法支付");
        }

        // 创建交易号
        long alipayAccountNo = alipayInfoService.getPayNo(orderId, userId);

        // 1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(URL, APP_ID, APP_PRIVATE_KEY, "json", CHARSET, ALIPAY_PUBLIC_KEY, SIGNTYPE);

        // 2、创建一个支付请求
        AlipayTradeWapPayRequest aliPayRequest = new AlipayTradeWapPayRequest();
        // 异步通知的地址
        aliPayRequest.setNotifyUrl(String.format("http://8cwxq3.natappfree.cc/api/alipay/tradeQuery?alipayAccountNo=%s", alipayAccountNo));
        JSONObject bizContent = new JSONObject();
        AlipayInfo alipayInfo = getAlipayInfoByAccount(String.valueOf(alipayAccountNo));
        // 商户订单号，商户网站订单系统中唯一订单号，必填。 支付宝流水帐号？？
        bizContent.put("out_trade_no", alipayInfo.getAlipayAccountNo());
        // 付款金额，必填
        bizContent.put("total_amount", alipayInfo.getTotalAmount());
        // 订单名称，必填
        bizContent.put("subject", "智能AI使用次数");
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        aliPayRequest.setBizContent(bizContent.toString());

        // 3、调用沙箱支付，得到响应结果
        AlipayTradeWapPayResponse aliPayResponse = alipayClient.pageExecute(aliPayRequest);
        ThrowUtils.throwIf(!aliPayResponse.isSuccess(), ErrorCode.PARAMS_ERROR, "AI调用失败");
        // 会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        if (aliPayResponse.isSuccess()) {
            response.setContentType("text/html;charset=" + CHARSET);
            String form = aliPayResponse.getBody();
            response.getWriter().write(form);
            response.getWriter().flush();
        }
    }

//    /**
//     * 支付接口
//     *
//     * @param alipayAccountNo
//     * @param response
//     * @throws AlipayApiException
//     * @throws IOException
//     */
//    @GetMapping("/pay")
//    public void pay(String alipayAccountNo, HttpServletResponse response) throws AlipayApiException, IOException {
//        ThrowUtils.throwIf(StringUtils.isBlank(alipayAccountNo),ErrorCode.PARAMS_ERROR);
//
//        // 1、根据支付宝的配置生成一个支付客户端
//        AlipayClient alipayClient = new DefaultAlipayClient(URL, APP_ID, APP_PRIVATE_KEY, "json", CHARSET, ALIPAY_PUBLIC_KEY, SIGNTYPE);
//
//        // 2、创建一个支付请求
//        AlipayTradeWapPayRequest aliPayRequest = new AlipayTradeWapPayRequest();
//        //异步通知的地址
//        aliPayRequest.setNotifyUrl( String.format("http://192.168.32.1:8101/api/alipay/tradeQuery?alipayAccountNo=%s",alipayAccountNo));
//        JSONObject bizContent = new JSONObject();
//        AlipayInfo alipayInfo = getAlipayInfoByAccount(alipayAccountNo);
//        //商户订单号，商户网站订单系统中唯一订单号，必填。 支付宝流水帐号？？
//        bizContent.put("out_trade_no", alipayInfo.getAlipayAccountNo());
//        // 付款金额，必填
//        bizContent.put("total_amount", alipayInfo.getTotalAmount());
//        // 订单名称，必填
//        bizContent.put("subject", "智能AI使用次数");
//        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
//        aliPayRequest.setBizContent(bizContent.toString());
//
//        // 3、调用沙箱支付，得到响应结果
//        AlipayTradeWapPayResponse aliPayResponse = alipayClient.pageExecute(aliPayRequest);
//        ThrowUtils.throwIf(!aliPayResponse.isSuccess(),ErrorCode.PARAMS_ERROR, "AI调用失败");
//        // 会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
//        if (aliPayResponse.isSuccess()) {
//            response.setContentType("text/html;charset=" + CHARSET);
//            String form = aliPayResponse.getBody();
//            response.getWriter().write(form);
//            response.getWriter().flush();
//        }
//    }


    /**
     * 支付成功回调，查询支付结果，更新表信息
     *
     * @throws AlipayApiException
     */
    @Transactional
    @PostMapping("/tradeQuery")
    public void tradeQuery(String alipayAccountNo) throws AlipayApiException {
        // 创建支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(URL, APP_ID, APP_PRIVATE_KEY, "json", CHARSET, ALIPAY_PUBLIC_KEY, SIGNTYPE);

        // 创建支付结果查询请求
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", alipayAccountNo);
        request.setBizContent(bizContent.toString());

        // 调用沙箱查询支付结果
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if (!response.isSuccess()) {
            log.error("查询交易结果失败");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询交易结果失败");
        }
        // 获取支付结果
        String resultJson = response.getBody();
        // 转map
        Map resultMap = JSON.parseObject(resultJson, Map.class);
        Map alipay_trade_query_response = (Map) resultMap.get("alipay_trade_query_response");
        String trade_status = (String) alipay_trade_query_response.get("trade_status");

        // 支付失败
        AlipayInfo alipayInfo = getAlipayInfoByAccount(alipayAccountNo);
        if (!Objects.equals(trade_status, "TRADE_SUCCESS")) {
            // 更新支付信息为支付失败
            alipayInfo.setPayStatus(Integer.valueOf(PayOrderEnum.TIMEOUT_ORDER.getValue()));
            boolean updateComplete = alipayInfoService.updateById(alipayInfo);
            ThrowUtils.throwIf(!updateComplete, ErrorCode.PARAMS_ERROR, "更新支付状态失败");

            log.info("支付回调成功，支付失败，结果：" + response.getBody());
            return;
        }

        // 支付成功
        // 1、更新支付信息
        alipayInfo.setPayStatus(Integer.valueOf(PayOrderEnum.COMPLETE.getValue()));
        boolean updateComplete = alipayInfoService.updateById(alipayInfo);
        ThrowUtils.throwIf(!updateComplete, ErrorCode.PARAMS_ERROR, "更新支付状态失败");

        // 2、更新订单状态
        AiFrequencyOrder order = getOrder(alipayInfo.getOrderId());
        order.setOrderStatus(Integer.valueOf(PayOrderEnum.COMPLETE.getValue()));
        boolean updateOrder = aiFrequencyOrderService.updateById(order);
        ThrowUtils.throwIf(!updateOrder, ErrorCode.PARAMS_ERROR, "更新订单失败");

        // 3、获取充值次数
        Long total = order.getPurchaseQuantity();
        Long userId = order.getUserId();
        AiFrequency aiFrequency = getAiFrequency(userId);
        // 更新次数
        Integer remainFrequency = aiFrequency.getRemainFrequency();
        aiFrequency.setRemainFrequency(remainFrequency + Math.toIntExact(total));
        boolean updateFrequency = aiFrequencyService.updateById(aiFrequency);
        ThrowUtils.throwIf(!updateFrequency, ErrorCode.PARAMS_ERROR, "更新次数失败");
        log.info("支付回调成功，支付成功，结果：" + response.getBody());
        //return ResultUtils.success(resultJson);
    }

    /**
     * 请求支付宝查询支付结果（仅用于测试）
     *
     * @param alipayAccountNo 支付交易号
     * @return 支付结果
     */
    @PostMapping("/query/payNo")
    @ResponseBody
    public void queryPayResultFromAlipay(String alipayAccountNo) {
        // 支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(URL, APP_ID, APP_PRIVATE_KEY, "json", CHARSET, ALIPAY_PUBLIC_KEY, SIGNTYPE);
        // 支付结果查询请求
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        try {
            bizContent.put("out_trade_no", alipayAccountNo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        request.setBizContent(bizContent.toString());
        // 沙箱执行查询
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if (!response.isSuccess()) {
            log.error("调用失败");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付成功，查询回调失败");
        }
        // 获取支付结果
        String resultJson = response.getBody();
        // 转map
        Map resultMap = JSON.parseObject(resultJson, Map.class);
        Map alipay_trade_query_response = (Map) resultMap.get("alipay_trade_query_response");
        // 支付结果
        String trade_status = (String) alipay_trade_query_response.get("trade_status");
        String total_amount = (String) alipay_trade_query_response.get("total_amount");
        String trade_no = (String) alipay_trade_query_response.get("trade_no");
    }

    /**
     * 根据支付宝流水账号获取支付信息
     *
     * @param alipayAccountNo
     * @return
     */
    public AlipayInfo getAlipayInfoByAccount(String alipayAccountNo) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("alipayAccountNo", alipayAccountNo);
        AlipayInfo aliPayOne = alipayInfoService.getOne(wrapper);
        ThrowUtils.throwIf(aliPayOne == null, ErrorCode.PARAMS_ERROR, "没有这个记录");
        return aliPayOne;
    }

    /**
     * 获取Ai次数订单
     *
     * @param orderId
     * @return
     */
    public AiFrequencyOrder getOrder(Long orderId) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("orderId", orderId);
        AiFrequencyOrder frequencyOrder = aiFrequencyOrderService.getById(orderId);
        if (frequencyOrder == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有这个记录");
        }
        return frequencyOrder;
    }

    /***
     * 获取Ai调用次数
     * @param userId
     * @return
     */
    public AiFrequency getAiFrequency(long userId) {

        QueryWrapper<AiFrequency> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        AiFrequency one = aiFrequencyService.getOne(wrapper);
        if (one == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return one;
    }
}
