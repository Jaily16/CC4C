# 后端模块划分  
## DAO层
- **UserDao**  
  维护的数据库表为：  
  user，administrator
- **ProgrammingLanguageDao**  
  维护的数据库表为：
  programming_language,language_documentation,user_subscribes_language
- **CourseDao**  
  维护的数据库表为：
  course,course_module,module_course,user_favors_course,administrator_updates_course
- **BlogDao**  
  维护的数据库表为：  
  blog,blog_involves_language,user_collects_blog,user_submits_blog,administrator_allows_blog
- **CommentDao**  
  维护的数据库表为： 
  comment,course_direct_comment,blog_direct_comment,indirect_comment
## Service层
```python
在service层中注意先写接口类，在其impl文件夹下写接口对应的实现类
```
- **UserService**  
  需要提供用户注册、邮箱验证、用户登录、管理员注册、管理员登录、用户信息修改、修改密码、用户选择最喜爱编程语言、查看历史记录等服务
- **ProgrammingLanguageService**  
  需要提供管理员添加编程语言、编程语言订阅、展示编程语言响应文档等服务
- **CourseService**  
  需要提供管理员添加课程、管理员添加课程模块、展示课程、对指定用户展示专属课程、管理员删除课程、用户收藏课程等服务
- **BlogService**  
  需要提供管理员审核博客、管理员发布博客、用户撰写博客、用户提交博客、用户收藏、展示博客、对指定语言展示专属博客、用户删除博客、管理员下架博客等服务
- **CommentService**  
  需要提供用户对课程评论、用户对博客评论、用户点赞评论、用户删除评论、用户对评论进行评论等服务
## Controller层
```python
controller层提供的api接口根据service层的功能和前端要求的访问而定，要求使用restful开发风格，对于前端的http请求，查询数据为get请求，提交数据为post请求，修改数据为put请求，删除数据为delete请求，不传入数据的请求一般为get请求，涉及隐私数据的采用post或put请求，按照前面service的划分一共5个controller
```  
**关于Code的约定** 
- 1** 信息
- 2** 成功
- 3** 重定向
- 4** 客户错误
- 5** 服务器错误  
```python
为了避免这个code约定重复，规定项目中的code均为5位数，以开头的数字区分错误类型，按照前面划分的模块，code中第二个数字：
user为0，ProgrammingLanguage为1，Course为2，Blog为3，comment为4。(示例：课程添加成功(2 2 000))
```