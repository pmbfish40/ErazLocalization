import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

external var localizationKeys: dynamic

fun createProject(name: String, alias: String, languages: Array<Pair<String, String>>): String {
    val json = createJson()
    json[name] = JSON.parse<Json>("{ \"name\" : \"$name\"," +
            "\"alias\" : \"$alias\" }")
    dbRef.update(json, fun(error: Any?) {
        if (error == null) {
            addLanguages(name, languages).then {
                alert(it)
            }
        } else {
            alert("error")
        }
    })
    return "success"
}

fun addScreens(name: String, vararg names: String) {
    addStrings("screens", name, *names)
}

fun addTypes(name: String, vararg  names: String) {
    addStrings("types", name, *names)
}

fun getProjects(completion: (Array<HashMap<String, String>>) -> Unit) {
    val projects = ArrayList<HashMap<String, String>>()
    dbRef.on(Constants.FIREBASE.contentType.VALUE, fun (snapshot: dynamic) {
        snapshot.forEach(fun (child: dynamic) {
            val element = hashMapOf<String, String>(
                    "name" to child.toJSON()["name"].toString(),
                    "alias" to child.toJSON()["alias"].toString()
            )
            projects.add(element)
        })
        console.log("get project")
        completion(projects.toTypedArray())
    })
}

fun getProject(name: String, listener: (Json) -> Unit) {
    val childRef = dbRef.child(name)
    childRef.on(Constants.FIREBASE.contentType.VALUE, fun (snapshot: dynamic) {
        val localizations = snapshot.toJSON()["localization"]
        if (localizations != null) {
            Object().values(localizations).forEach(fun(value: dynamic) {
                Object().values(value).forEach(fun(valu: Json) {
                    localizationKeys.add(valu["key"].toString())
                })
            })
        }
        listener(snapshot.toJSON() as Json)
    })
}

fun addLanguages(name: String, languages: Array<Pair<String, String>>): Promise<String> {
    val childRef = dbRef.child("$name/languages")
    return Promise { success, failure ->
        childRef.once(Constants.FIREBASE.contentType.VALUE)
                .then(fun(snapshot: dynamic) {
                    val snapshotArray = js("Object").values(snapshot.toJSON())
                    var needsToUpdate = false
                    for (language in languages) {
                        val element = json("langCode" to language.first,
                                "langName" to language.second)
                        var contains = false
                        snapshotArray.forEach(fun(elem: dynamic) {
                            if (elem["langCode"] == element["langCode"]) {
                                contains = true
                            }
                        })
                        if (!contains) {
                            snapshotArray.push(element)
                            needsToUpdate = true
                        }
                    }
                    if (needsToUpdate) {
                        childRef.set(snapshotArray, fun(error: Any?) {
                            if (error == null) {
                                success("success")
                            } else {
                                failure(Throwable("error"))
                            }
                        })
                    }
                })
                .catch(fun(_: dynamic) {
                    val snapshotArray = json()
                    for (language in languages) {
                        snapshotArray[languages.indexOf(language).toString()] = json("langCode" to language.first,
                                "langName" to language.second)
                    }
                    childRef.set(snapshotArray, fun(error: Any?) {
                        if (error == null) {
                            success("success")
                        } else {
                            failure(Throwable("error"))
                        }
                    })
                }) as? Unit
    }

}

fun filterScreens(projectName: String, name: String, callBack: (Json) -> Unit) {
    val project = dbRef.child("$projectName/localization/$name")
    project.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                callBack(snapshot.toJSON() as Json)
            })
            .catch(fun (error: Throwable) {
                alert(error.message)
            })
}

fun setEditing(editing: Boolean, projectName: String, screen: String, key: String) {
    val childRef = dbRef.child("$projectName/localization/$screen")
    childRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                if (jsTypeOf(snapshot) != "undefined" && snapshot.toJSON() != null) {
                    var index = ""
                    Object().values(snapshot.toJSON()).find(fun (el: dynamic, idx: dynamic): Boolean {
                        index = idx.toString()
                        return el.key == key
                    })
                    childRef.child("$index/isEditing").set(editing)
                }
            })
}


/// Helpers

fun addStrings(child: String, name: String, vararg names: String) {
    val array = js("[]")
    for (_name in names) {
        array.push(_name)
    }
    val childRef = dbRef.child("$name/$child")
    childRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                var json = snapshot.toJSON()
                if (json == null) {
                    json = createJson()
                }
                val values = Object().values(json)
                for (_name in names) {
                    json[values.length + names.indexOf(_name)] = name
                }
                childRef.update(json, fun(error: Any?) {
                    if (error == null) {
                        console.log("success")
                    } else {
                        console.log(error)
                    }
                })
            })
}

fun removeValueFromChildArray(value: String, child: String, name: String) {
    val childRef = dbRef.child("$name/$child")
    childRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                val  json = snapshot.toJSON()
                val values = Object().values(json)
                val index = values.indexOf(value).toString()
                if (index != undefined) {
                    for (i in index.toInt()..((values.length - 1) as Int)) {
                        json[i.toString()] = json[(i + 1).toString()]
                    }
                    json[(values.length - 1).toString()] = null
                    console.log(JSON.stringify(json, null, 4))
                    childRef.update(json, fun(error: Any?) {
                        if (error == null) {
                            console.log("$value of $child deleted")
                        } else {
                            console.log(error)
                        }
                    })
                }
            })
}

fun removeLocalizaton(name: String, screen: String, key: String, lang_value: String) {
    val childRef = dbRef.child("$name/localization/$screen")
    var indexOfScreen = ""
    childRef.once(Constants.FIREBASE.contentType.VALUE, fun (snapshot: dynamic) {
        val values = Object().values(snapshot.toJSON())
        val localization = values.find(fun (el: dynamic, idx: dynamic): Boolean {
            indexOfScreen = Object().keys(snapshot.toJSON())[idx.toString().toInt()].toString()
            return el["key"] == key
        })
        val localizationValues = Object().values(localization["values"])
        var index = ""
        val  itemToDelete = localizationValues.find(fun (el: dynamic, idx: dynamic): Boolean {
            index = idx.toString()
            return el["lang_value"] == lang_value
        })
        itemToDelete["lang_value"] = ""
        childRef.child("$indexOfScreen/values/$index").set(itemToDelete, fun (error: Any?) {
            if (error == null) {
                console.log(JSON.stringify(itemToDelete, null, 4))
            } else {
                console.log(error)
            }
        })

    })
}

fun removeRow(projectName: String, screen: String, key: String) {
    val childRef = dbRef.child("$projectName/localization/$screen")
    childRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                val json = snapshot.toJSON()
                val values = Object().values(json)
                var index = ""
                values.find(fun (el: dynamic, idx: dynamic): Boolean {
                    index = idx.toString()
                    return el.key == key
                })
                if (values.length > 1) {
                    for (i in index.toInt()..((values.length - 1) as Int)) {
                        json[i.toString()] = json[(i + 1).toString()]
                    }
                    json[(values.length - 1).toString()] = null
                } else {
                    json["0"] = null
                    removeValueFromChildArray(screen, "screens", projectName)
                }
                childRef.update(json, fun (error: Any?) {
                    if (error != null) {
                        alert(error.toString())
                    } else {
                        localizationKeys.delete(key)
                    }
                })
            })
}

fun editLocalization(name: String, screen: String, key: String, languageCode: String, value: String) {
    val childRef = dbRef.child("$name/localization/$screen")
    childRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                var indexOfScreen = ""
                val values = Object().values(snapshot.toJSON())
                val localization = values.find(fun (el: dynamic, idx: dynamic): Boolean {
                    indexOfScreen = Object().keys(snapshot.toJSON())[idx.toString().toInt()].toString()
                    return el["key"] == key
                })
                val localizationValues = Object().values(localization["values"])
                var index = ""
                localizationValues.find(fun (el: dynamic, idx: dynamic): Boolean {
                    index = idx.toString()
                    return el["lang_key"] == languageCode
                })
                childRef.child("$indexOfScreen/values/$index/lang_value").set(value, fun (error: Any?) {
                    if (error == null) {
                        console.log(value)
                    } else {
                        console.log(error)
                    }
                })
            })
}

fun deleteProject(name: String) {
    dbRef.once(Constants.FIREBASE.contentType.VALUE)
            .then(fun (snapshot: dynamic) {
                val newValue = snapshot.toJSON()
                newValue[name] = null
                dbRef.update(newValue, fun (error: Any?) {
                    if (error != null) {
                        console.log("success")
                    } else {
                        console.log(error)
                    }
                })
            })
}

@JsName("existKeyInProject")
fun existKeyInProject(key: String): Boolean {
    return localizationKeys.has(key)
}

fun createJson(): dynamic {
    return js("{}")
}

external fun delete(p: dynamic): Boolean = definedExternally