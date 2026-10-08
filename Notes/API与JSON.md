# 我的API与JSON学习笔记

参考资料：[HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)、[JSON 标准](https://www.rfc-editor.org/rfc/rfc8259.html)、[Jackson](https://github.com/FasterXML/jackson-databind)。

## 什么是API
API 是程序之间调用功能的约定。后端接口通常通过 HTTP 提供，前端按约定传入参数，后端返回结果。

- 一个接口要约定方法、路径、参数、返回格式和错误处理
- 远程接口还要处理认证、权限、网络异常和超时

## API问题：怎么设计获取笔记详情的接口
可以设计为 `GET /api/notes/{id}`，根据笔记编号返回详情。

- 方法与路径：`GET /api/notes/1001`
- 输入参数：路径里的笔记编号，检查格式是否合法
- 用户身份：从经过验证的登录凭证获取，不能直接相信前端传来的用户编号
- 访问权限：公开笔记按规则开放，私有笔记检查当前用户是否有权查看
- 返回内容：编号、标题、正文、发布者和发布时间，不返回密码等无关字段
- 错误结果：参数错误用 `400`，未认证用 `401`，无权限用 `403`，不存在用 `404`

项目也可以对无权访问的私有笔记统一返回 `404`，避免暴露它是否存在，但前后端需要约定一致。

成功响应的 JSON 可以是：

```json
{
  "id": 1001,
  "title": "Redis学习笔记",
  "content": "Redis 主要把数据存在内存中。",
  "authorId": 12,
  "createdAt": "2026-10-08T10:00:00+08:00"
}
```

上面的内容是接口示例数据，不是实际查询结果。

## 接口内部怎么处理
收到请求后，检查参数和权限，再查询数据，最后把结果转成 JSON 返回。

> 请求 → 参数与身份检查 → 权限检查 → 查询数据 → JSON 响应

- 可以先查 Redis，未命中时再查 MySQL
- 即使命中缓存，也要按当前用户检查访问权限
- 返回的 `Content-Type` 通常是 `application/json`

## 什么是JSON
JSON 是一种文本形式的数据交换格式，不同语言都可以把它转换为自己的对象。

## JSON问题1：数据长什么样
JSON 支持字符串、数字、布尔值、null、对象和数组。

```json
{
  "id": 1001,
  "title": "我的笔记",
  "published": true,
  "tags": ["Java", "Redis"],
  "summary": null
}
```

- 对象用 `{}`，数组用 `[]`
- 字段名和字符串使用双引号
- 布尔值是 `true` 或 `false`，空值是 `null`
- 标准 JSON 不支持注释和末尾多余的逗号
- 日期没有单独的 JSON 类型，一般约定用字符串表示

## JSON问题2：Java对象怎么与JSON互相转换
可以使用 Jackson。对象转为 JSON 叫序列化，JSON 转为对象叫反序列化。

下面以 Jackson 2 的 `com.fasterxml.jackson.databind.ObjectMapper` 为例，项目需要添加兼容的 `jackson-databind` 依赖。

对象类：

```java
public class NoteDTO {
    public Long id;
    public String title;
    public String content;
}
```

转换代码：

```java
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();
NoteDTO note = new NoteDTO();
note.id = 1001L;
note.title = "Redis学习笔记";
note.content = "Redis 主要把数据存在内存中。";

String json = mapper.writeValueAsString(note); // 对象转 JSON
NoteDTO restored = mapper.readValue(json, NoteDTO.class); // JSON 转对象
```

上面是方法调用片段，可以放进声明 `throws Exception` 的方法中练习。

## 实践注意事项
- 不通过手动拼接字符串生成 JSON，交给序列化工具处理引号、换行等转义
- 请求能转换成对象，不代表字段合法，还要校验标题长度、正文大小等条件
- 接口返回用专门的数据对象，避免把用户密码或内部字段一起发出去
- 前后端统一字段名、时间格式、空值和错误响应格式

## 学习过程记录
- 本次从笔记详情接口出发，整理了路径、参数、权限和响应设计
- 对照 JSON 标准和 Jackson 资料，写了对象转换示例
- 待实践：运行转换代码，再调用实际接口检查返回数据；本次未运行示例或启动接口服务
