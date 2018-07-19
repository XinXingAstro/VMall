package com.vmall.manager.service.impl;


import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.vmall.common.pojo.EasyUIDataGridResult;
import com.vmall.common.pojo.VMallResult;
import com.vmall.common.utils.IDUtils;
import com.vmall.common.utils.JsonUtils;
import com.vmall.jedis.JedisClient;
import com.vmall.manager.service.ItemService;
import com.vmall.mapper.TbItemDescMapper;
import com.vmall.mapper.TbItemMapper;
import com.vmall.pojo.TbItem;
import com.vmall.pojo.TbItemDesc;
import com.vmall.pojo.TbItemExample;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.jms.*;
import java.util.Date;
import java.util.List;

/**
 * 商品管理Service
 */
@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private TbItemMapper itemMapper;
    @Autowired
    private TbItemDescMapper itemDescMapper;
    @Autowired
    private JmsTemplate jmsTemplate;
    @Resource(name="itemAddTopic")
    private Destination destination;
    @Autowired
    private JedisClient jedisClient;

    @Value("${ITEM_INFO}")
    private String ITEM_INFO;
    @Value("${ITEM_EXPIRE}")
    private Integer TIEM_EXPIRE;

    @Override
    public TbItem getItemById(long itemId) {
        //查询数据库之前先查询缓存
        try {
            String json = jedisClient.get(ITEM_INFO + ":" + itemId  + ":BASE");
            if (StringUtils.isNotBlank(json)) {
                // 把json数据转换成pojo
                TbItem tbItem = JsonUtils.jsonToPojo(json, TbItem.class);
                return tbItem;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //缓存中没有查询数据库
        TbItem item = itemMapper.selectByPrimaryKey(itemId);
        try {
            //把查询结果添加到缓存
            jedisClient.set(ITEM_INFO + ":" + itemId  + ":BASE", JsonUtils.objectToJson(item));
            //设置过期时间，提高缓存的利用率
            jedisClient.expire(ITEM_INFO + ":" + itemId  + ":BASE", TIEM_EXPIRE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return item;
    }

    @Override
    public EasyUIDataGridResult getItemList(int page, int rows) {
        //设置分页信息
        PageHelper.startPage(page, rows);
        //执行查询
        TbItemExample example = new TbItemExample();
        List<TbItem> list = itemMapper.selectByExample(example);
        //取查询结果
        PageInfo<TbItem> pageInfo = new PageInfo<>(list);
        EasyUIDataGridResult result = new EasyUIDataGridResult();
        result.setRows(list);
        result.setTotal(pageInfo.getTotal());
        //返回结果
        return result;
    }

    @Override
    public VMallResult addItem(TbItem item, String desc) {
        //生成商品id
        final long itemId = IDUtils.genItemId();
        //补全item的属性
        item.setId(itemId);
        //商品状态，1-正常，2-下架，3-删除
        item.setStatus((byte) 1);
        item.setCreated(new Date());
        item.setUpdated(new Date());
        //向商品表插入数据
        itemMapper.insert(item);
        //创建一个商品描述表对应的pojo
        TbItemDesc itemDesc = new TbItemDesc();
        //补全pojo的属性
        itemDesc.setItemId(itemId);
        itemDesc.setItemDesc(desc);
        itemDesc.setUpdated(new Date());
        itemDesc.setCreated(new Date());
        //向商品描述表插入数据
        itemDescMapper.insert(itemDesc);
        //向Activemq发送商品添加消息
        jmsTemplate.send(destination, new MessageCreator() {

            @Override
            public Message createMessage(Session session) throws JMSException {
                //发送商品id
                TextMessage textMessage = session.createTextMessage(itemId + "");
                return textMessage;
            }
        });
        //返回结果
        return VMallResult.ok();
    }

    @Override
    public TbItemDesc getItemDescById(long itemId) {
        //查询数据库之前先查询缓存
        try {
            String json = jedisClient.get(ITEM_INFO + ":" + itemId  + ":DESC");
            if (StringUtils.isNotBlank(json)) {
                // 把json数据转换成pojo
                TbItemDesc tbItemDesc = JsonUtils.jsonToPojo(json, TbItemDesc.class);
                return tbItemDesc;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //缓存中没有查询数据库
        TbItemDesc itemDesc = itemDescMapper.selectByPrimaryKey(itemId);
        try {
            //把查询结果添加到缓存
            jedisClient.set(ITEM_INFO + ":" + itemId  + ":DESC", JsonUtils.objectToJson(itemDesc));
            //设置过期时间，提高缓存的利用率
            jedisClient.expire(ITEM_INFO + ":" + itemId  + ":DESC", TIEM_EXPIRE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return itemDesc;
    }

}