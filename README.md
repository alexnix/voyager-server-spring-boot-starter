# voyager-server-spring-boot-starter
[![](https://jitpack.io/v/alexnix/voyager-server-spring-boot-starter.svg)](https://jitpack.io/#alexnix/voyager-server-spring-boot-starter)

## What is Voyager Server?

Voyager Server is a set of helper classes and interfaces that help creating REST endpoints for models, including easy **Access Control Level (ACL) and before/after create/read/update/delete hooks** (see [ACLs](#acls) and [Hooks](#hooks) for details). 

The following endpoints are generated:

- POST <CONTROLLER_REQUEST_MAPPING>/
  - Receives the entity to be created in the body
- GET  <CONTROLLER_REQUEST_MAPPING>/
  - Supports sort_by query parameter with the following format `sort_by=<field>:<asc/desc>` (for example, `sort_by=createdAt:desc`)
  - Supports pagination by query parameters:
    - page_no
      - If not present, will default to 0
    - page_size
      - If not present, will default to 20
  - Supports filtering by specifying the fields to filter by as query parameters in the format `<field>=<value>` or  `<field>=<operation>:<operation term>`, for example:
    - name=John
    - name=eq:"John"
      - Note that when the operation is explicit, strings **must** be warped in **double quotes**
    - name=neq:"John"
    - name=in:"John","Bob"
      - Multiple values can be delimited by comma, if the operation is of such nature that it accepts an array of terms
    - age=gte:10
      - If no double quotes are used (such as in this case) the value will be parsed to a number
    - gender=eq:null
      - In no double quotes are used (such as in this case), and the value is "null", then it will be parsed as `null`
- GET  <CONTROLLER_REQUEST_MAPPING>/{id}
- PUT  <CONTROLLER_REQUEST_MAPPING>/{id}
  - Receives the full (not just the changed fields) updated entity in the body
  - The id of an entity will not be changed, event if send in the body
- DELETE  <CONTROLLER_REQUEST_MAPPING>/{id}

## Add to your project

### Gradle

**Step 1.** Add the JitPack repository to your build file

```groovy
allprojects {
    repositories {
        ...
    	maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency

```groovy
dependencies {
	implementation 'com.github.alexnix:voyager-server-spring-boot-starter:<VERSION>'
}
```

&lt;VERSION> may be a commit hash, for example 63d7ee5

### Maven

**Step 1.** Add the JitPack repository to your build file

```markup
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Step 2.** Add the dependency

```markup
<dependency>
    <groupId>com.github.alexnix</groupId>
    <artifactId>voyager-server-spring-boot-starter</artifactId>
    <version>VERSION</version>
</dependency>
```

&lt;VERSION> may be a commit hash, for example 63d7ee5

## How to use

**Step 1**. Create a model that extends HasId

Kotlin

```kotlin
@Entity
@Table(name="books")
data class Book(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long = 0,
    
    val title:String,
    
    val author:String
): HasId
```

Java

```java
@Entity
@Table(name = "books")
public class Book implements HasId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String title;

    public String author;

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }
    
    ... the rest of the getters and setters ...
}
```



**Step 2**. Create a repository that extends a VoyagerRepository<T> (for example VoyagerRepositoryJPA<T>)

Kotlin

```kotlin
interface BookRepo: VoyagerRepositoryJPA<Book>
```

Java

```java
public interface BookRepo extends VoyagerRepositoryJPA<Book> {
}
```



**Step 3**. Create a service that extends a VoyagerService<T> (for example VoyagerServiceJPA<T>)

Kotlin

```kotlin
@Service
class BookService(private val repo: BookRepo): VoyagerServiceJPA<Book>(repo)
```

Java

```java
@Service
public class BookService extends VoyagerServiceJPA<Book> {
    public BookService(@NotNull VoyagerRepositoryJPA<Book> repo) {
        super(repo);
    }
}
```



**Step 4**. Create a controller that extends VoyagerController<T, R>

Kotlin

```kotlin
@RestController
@RequestMapping("/books")
class BookCtrl(r: BookService): VoyagerController<Book, BookService>(r)
```

Java

```java
@RestController
@RequestMapping("/books")
public class BookCtrl extends VoyagerController<Book, BookService> {
    public BookCtrl(@NotNull BookService service) {
        super(service, new DefaultACL<>(), new DefaultHooks<>());
    }
}
```



## ACLs

ACL is a way to enforce access permission for the create/read/update/delete operations. To do so, there are two options:

- provide a custom ACL object (extends ACL) in the controller constructor, or
- override the corresponding functions in the controller

The following code snippet prohibits any delete operations:

Kotlin

```kotlin
override fun deleteAcl(item: Book) = false
```

Java

```java
@Override
public boolean deleteAcl(@NotNull Book item) {
	return false;
}
```

Complex permission logic can be implemented inside those methods, by looking at the current user object (obtained from spring-security) and at the object being changed. For example to restrict deleting of objects, just the their owner of the an administrator. Such logic would look like:

```kotlin
override fun deleteAcl(item: Book) = currentUser.roles.includes("admin") || currentUser.id == book.owner_id
```

## Hooks

Hooks provide a way to react to events before and after they happen. Note, that hooks cannot enforce permissions (ACLs should be used for this) *but* the `beforeReadMany` hook provides a way to mutate the query filters, so this can, for example, restrict a uses from reading all entities, with the exception of his own, by programmatically adding a filter such as: `owner_id=eq:<current_used.id>`.

Same as ACLs, hooks can be implemented in two different options:

- provide a custom Hooks object (extends Hooks) in the controller constructor, or
- override the corresponding functions in the controller

Additionally, whatever the `after` hooks return will be returned in the HTTP response. So this functionality can be used to send responses that deviate from the classic REST responses.

Such responses are marked with the `__voyager_api` JSON key and must follow the following schema:

```json
{
	__voyager_api: true,
	post?: Object[],
	put?: Object[],
	delete?: Object[],
}
```

This is useful for informing the client of side effects, in response to an event, for example creating a restaurant review will create (post) the review object and will update (put) the restaurant object, by adjusting its rating. In this scenario, the VoyagerResponse object will have the appropriate values in the `post` and `put` fields. 

The following table describes typical use cases for each hook. Where empty, there was not a clear use case for them, but where added for symmetry. 

|        | Create                                                       | Read Many                                                    | Read One                                             | Update                                                       | Delete                                                       |
| ------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| Before | Add server generated fields, such as owner id or createdAt   | Add filters (query where clauses), for example to restrict the query only to entities where owner_id == user_id |                                                      |                                                              |                                                              |
| After  | Create/update/delete   side effect entities and return them in a VoyagerResponse object |                                                              | Populate foreign keys fields with the actual objects | Create/update/delete   side effect entities and return them in a VoyagerResponse object | Create/update/delete   side effect entities and return them in a VoyagerResponse object |



## Demo Projects

- [Java](https://github.com/alexnix/voyager-server-java-example)
- Kotlin [coming soon]