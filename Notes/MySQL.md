# **我的MySQL学习笔记**

我选择的教程是菜鸟教程的[MySQL教程 | 菜鸟教程](https://www.runoob.com/mysql/mysql-tutorial.html)

## 什么是MySQL

MySQL是一个**RDBMS**(Relational Database Management System：关系数据库管理系统)应用软件

### 什么是RDBMS

Relational Database Management System，关系数据库管理系统，是一种用于管理关系型数据库的程序。关系型数据库是指建立在关系模型基础上的数据库。

这种所谓的“关系型”可以理解为“表格”的概念，一个关系型数据库由一个或数个表格组成。表格是由互相关联的数据条目构成的，它由行和列组成。

### MySQL的开关

**开启**

- `net start mysql`打开MySQL服务器（后台服务器，监听3306端口）MySQL设置为开机自启动就不需要手动打开服务器

- `mysql -uroot -p`客户端连接这个后台服务，进入SQL交互环境

**关闭**

- `net stop mysql`关闭服务器
  
## 数据库的管理

### 创建

>`CREATE DATABASE [IF NOT EXISTS] database_name`
`[CHARACTER SET charset_name]` 指定字符集
`[COLLATE collation_name];` 指定排序/校验规则（校对集）

### 删除

>`DROP DATABASE [IF EXISTS] <database_name>;`

### 选择

>`USE database_name;`

## 数据表的管理

### 创建

>`CREATE TABLE table_name (`
>`    column1 datatype,`
>`    column2 datatype,`
>`    ...`
>`);`

`)`后可以加子句，如：

- CHARACTER SET 指定字符集
- COLLATE 指定排序/校验规则（校对集）
- ENGINE 设置存储引擎
- CHARSET 设置编码

字段也可以设置属性：

- NOT NULL 字段不为空
- AUTO_INCREMENT 定义列为自增的属性，一般用于主键，数值会自动加 1
- PRIMARY KEY 关键字用于定义列为主键。 您可以使用多列来定义主键，列间以逗号 , 分隔

### 删除

>`DROP TABLE [IF EXISTS] table_name;`

## 数据管理

### 插入

>`INSERT INTO table_name (column1, column2, column3, ...)`
>`VALUES (value1, value2, value3, ...);`

如果省略列名，值必须按表结构顺序填写；自增主键可以填写 `NULL`：

>`INSERT INTO users`
>`VALUES (NULL,'test', 'test@runoob.com', '1990-01-01', true);`

### 查询

>`SELECT column1, column2, ...`
>`FROM table_name`
>`[WHERE condition]`
>`[ORDER BY column_name [ASC | DESC]]`
>`[LIMIT number];`

选择所有列可以用 `*` 代替具体列名

### 更新

>`UPDATE table_name`
>`SET column1 = value1, column2 = value2, ...`
>`WHERE condition;`

如果不提供 `WHERE` 子句，将更新表中的所有行

### 删除

>`DELETE FROM table_name`
>`WHERE condition;`

如果没有指定 `WHERE` 子句，MySQL 表中的所有记录将被删除

### `LIKE`

`LIKE` 子句是在 MySQL 中用于在 `WHERE` 子句中进行模糊匹配的关键字。它通常与通配符一起使用，用于搜索符合某种模式的字符串

`LIKE` 子句中使用`%`来表示任意字符，如果没有使用`%`, `LIKE` 子句与等号 = 的效果是一样的。

>`SELECT column1, column2, ...`
>`FROM table_name`
>`WHERE column_name LIKE pattern;`

- `%` 通配符表示零个或多个字符
- `_` 通配符表示一个字符

### `UNION`

MySQL `UNION` 操作符用于连接两个以上的 `SELECT` 语句的结果组合到一个结果集合，并去除重复的行

`UNION` 操作符必须由两个或多个 `SELECT` 语句组成，每个 `SELECT` 语句的列数必须相同，对应列的数据类型需要兼容

- `UNION` 操作符在合并结果集时会去除重复行
- `UNION ALL` 不会去除重复行


### `ORDER BY`

>`ORDER BY column1 [ASC | DESC], column2 [ASC | DESC], ...;`

ASC 表示升序（默认），DESC 表示降序

>`ORDER BY price IS NULL, price DESC;`
>`ORDER BY price IS NOT NULL, price DESC;`

用于处理 NULL 值

### `GROUP BY`

`GROUP BY` 语句根据一个或多个列对结果集进行分组

>`GROUP BY column1;`

### `JOIN`

- `INNER JOIN`（内连接,或等值连接）：获取两个表中满足连接条件的匹配行

>`INNER JOIN table2 ON table1.column_name = table2.column_name;`

- `LEFT JOIN`（左连接）：返回左表的所有行，并包括右表中匹配的行，如果右表中没有匹配的行，将返回 NULL 值

>`LEFT JOIN table2 ON table1.column_name = table2.column_name;`

- `RIGHT JOIN`（右连接）：返回右表的所有行，并包括左表中匹配的行，如果左表中没有匹配的行，将返回 NULL 值

>`RIGHT JOIN table2 ON table1.column_name = table2.column_name;`

### NULL 值处理

在 MySQL 中，NULL 值与任何其它值的比较（即使是 NULL）永远返回 NULL，即 NULL = NULL 返回 NULL

- `IS NULL`: 当列的值是 NULL,此运算符返回 true
- `IS NOT NULL`: 当列的值不为 NULL, 运算符返回 true
- `<=>`: 比较操作符（不同于 = 运算符），当比较的的两个值相等或者都为 NULL 时返回 true

### 正则表达式

[表格](https://www.runoob.com/mysql/mysql-regexp.html)

- `REGEXP` :用于检查一个字符串是否匹配指定的正则表达式模式

>`WHERE column_name REGEXP 'pattern';`

- `RLIKE` :和 REGEXP 可以互换使用，没有区别

## 启蒙篇问题回答
### 1. MySQL可以承载什么数量级的数据
单表百万到千万行是常见规模，也可以保存更多数据，但查询速度取决于表结构、索引、SQL、硬件和并发量。

- JotangNote 的数万用户、数十万笔记属于常见的数据规模
- “单表 2000 万行”不是统一上限，也不能只凭行数保证查询速度
- 索引和磁盘空间也会占用资源，正文越长，实际容量越大

### 2. 为什么大量数据仍然能较快查询和修改
MySQL 通过索引、缓存、查询优化和事务机制，减少扫描、磁盘访问和并发冲突。

- 索引：InnoDB 常见索引用 B+ 树组织，可以快速定位符合条件的数据
- 缓冲池：在内存里缓存数据页和索引页，减少重复读取磁盘
- 查询优化器：选择较合适的查询方式，可以用 `EXPLAIN` 查看计划
- 事务、MVCC 和锁：保证数据处理的一致性，并支持多个请求并发访问
- 日志：redo 用于崩溃恢复，undo 支持回滚和历史版本；数据安全也取决于配置

有索引也不是所有查询都会快，索引还会增加写入成本，需要根据查询条件设计。

### 3. 怎么通过Java代码操作MySQL
添加 MySQL Connector/J 驱动，通过 JDBC 建立连接、执行 SQL，再读取结果。下面假设已有 `jotang_note` 数据库和包含 `id`、`title` 的 `notes` 表。

```java
import java.sql.*;

String url = "jdbc:mysql://localhost:3306/jotang_note";
String sql = "SELECT id, title FROM notes WHERE id = ?";
try (Connection connection = DriverManager.getConnection(
         url, "app_user", "你的密码");
     PreparedStatement statement = connection.prepareStatement(sql)) {
    statement.setLong(1, 1001L);
    try (ResultSet result = statement.executeQuery()) {
        if (result.next()) {
            System.out.println(result.getString("title"));
        }
    }
}
```

- 这是方法内的调用片段，需要处理 `SQLException` 或声明抛出异常
- 查询用 `executeQuery()`，插入、修改和删除通常用 `executeUpdate()`
- 参数用 `?` 和 `PreparedStatement` 绑定，避免把用户输入直接拼进 SQL
- 多条相关修改可以关闭自动提交，成功后 `commit()`，失败时 `rollback()`
- 项目中可以使用连接池和 MyBatis、JPA 等工具，减少重复代码

参考资料：[索引](https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html)、[缓冲池](https://dev.mysql.com/doc/refman/8.4/en/innodb-buffer-pool.html)、[Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-connect-drivermanager.html)。

## 学习过程记录
- 原笔记已记录服务启停、数据库与表管理，以及常见 SQL 的作用
- 本次补充了容量、索引与缓存的理解，并写了 JDBC 查询示例
- 待实践：建表插入数据，用 Java 查询，再用 `EXPLAIN` 对比索引效果；本次没有连接数据库或运行代码
