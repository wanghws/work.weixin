import com.aliyun.openservices.shade.com.alibaba.fastjson.JSON;
import com.aliyun.openservices.shade.com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CorpWeixinUtils {
    private final static Logger log = LoggerFactory.getLogger(CorpWeixinUtils.class);

    private final static String CORPID = "123";
    private final static String AGENTID = "123";
    public final static String SECRET = "123";

    private static ShardedJedisPool shardedJedisPool = null;
    private static ShardedJedis redis = shardedJedisPool.getResource();
    private static OkHttpClient client = new OkHttpClient();

    public static String get(String url) throws Exception{
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        okhttp3.Response response = client.newCall(request).execute();
        return response.body().string();
    }
    public static String postBody(String url,String params) throws Exception{
        RequestBody body = RequestBody.create(MediaType.parse("text/xml; charset=utf-8"),params);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        okhttp3.Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public static String getAccessToken(ShardedJedis redis,String agentId,String secret)throws Exception{

        String accessToken = (String)redis.get("weixin_access_token_"+agentId);
        if (!Strings.isNullOrEmpty(accessToken))return accessToken;
        String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="+CORPID+"&corpsecret="+secret;
        String body = get(url);
        Map<String,Object> map = new JSONObject().parseObject(body,Map.class);
        accessToken = (String)map.get("access_token");
        if (null==accessToken)throw new Exception("");

        redis.set("weixin_access_token_"+agentId,accessToken);
        redis.expire("weixin_access_token_"+agentId,7200);//7200

        return accessToken;
    }

    public static String getAccessToken(ShardedJedis redis)throws Exception{
        return getAccessToken(redis,AGENTID,SECRET);
    }

    public static String getUserId(String accessToken,String code)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo?access_token="+accessToken+"&code="+code;
        String body = get(url);
        Map<String,Object> map = new JSONObject().parseObject(body,Map.class);
        String userId = (String) map.get("UserId");
        return userId;
    }

    public static Map<String,Object> getUserInfo(String accessToken,String userId)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token="+accessToken+"&userid="+userId;
        String body = get(url);
        Map<String,Object> map = new JSONObject().parseObject(body,Map.class);
        return map;
    }

    public static DepartmentResponse getDepartments(String accessToken, String parentId)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token="+accessToken+"&id="+parentId;
        String body = get(url);
        log.debug(body);
        DepartmentResponse response = new JSONObject().parseObject(body,DepartmentResponse.class);
        return response;
    }
    public static DepartmentUserResponse getDepartmentUserList(String accessToken, Long departmentId)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token="+accessToken+"&fetch_child=0&department_id="+departmentId;
        String body = get(url);
        DepartmentUserResponse response = new JSONObject().parseObject(body,DepartmentUserResponse.class);
        return response;
    }

    public static Response messageSend(String accessToken,List<String> userList,List<String> deptList,List<String> tagList,String content)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token="+accessToken;
        Map<String,Object> params = new HashMap<>();

        if(null!=userList && !userList.isEmpty())params.put("touser", StringUtils.join(userList,"|"));
        if(null!=deptList && !deptList.isEmpty())params.put("toparty",StringUtils.join(deptList,"|"));
        if(null!=tagList && !tagList.isEmpty())params.put("totag",StringUtils.join(tagList,"|"));

        params.put("msgtype","text");
        params.put("agentid",AGENTID);
        Map<String,String> map = new HashMap<>();
        map.put("content",content);
        params.put("text",map);
        params.put("safe","0");

        String json = JSON.toJSONString(params);
        String body = postBody(url,json);
        return new JSONObject().parseObject(body,Response.class);
    }

    public static ChatGroupResponse createChatGroup(String accessToken,String name,List<String> userList)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token="+accessToken;
        Map<String,Object> params = new HashMap<>();
        params.put("name",name);
        params.put("userlist", userList);

        String json = JSON.toJSONString(params);
        String body = postBody(url,json);
        log.info(body);
        ChatGroupResponse response = new JSONObject().parseObject(body,ChatGroupResponse.class);
        return response;
    }

    public static ChatGroupResponse changeChatGroup(String accessToken,String chatId,String name,List<String> addUserList,List<String> removeUserList)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/update?access_token="+accessToken;
        Map<String,Object> params = new HashMap<>();
        params.put("name",name);
        params.put("chatid",chatId);
        if(null!=addUserList&&addUserList.size()>0)params.put("add_user_list", addUserList);
        if(null!=removeUserList&&removeUserList.size()>0)params.put("del_user_list", removeUserList);
       
        String json = JSON.toJSONString(params);
        String body = postBody(url,json);
        log.info(body);
        ChatGroupResponse response = new JSONObject().parseObject(body,ChatGroupResponse.class);
        return response;
    }


    public static Response messageChatGroupSend(String accessToken,String chatId,String content)throws Exception{
        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/send?access_token="+accessToken;
        Map<String,Object> params = new HashMap<>();

        params.put("chatid",chatId);
        params.put("msgtype","text");
        params.put("agentid",AGENTID);
        Map<String,String> map = new HashMap<>();
        map.put("content",content);
        params.put("text",map);
        params.put("safe","0");

        String json = JSON.toJSONString(params);
        String body = postBody(url,json);
        //log.info(body);
        return new JSONObject().parseObject(body,Response.class);
    }