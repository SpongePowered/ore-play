//=====> EXTERNAL CONSTANTS

var PROJECTS_PER_PAGE;
var START_URL;
var DEFAULT_SORT;

var query;
var categories = [];
var tags = [];
var owner;
var sort;
var relevance;

var page = 1;

var visibilityCssClasses = {
    new: "project-new",
    needsChanges: "striped project-needsChanges",
    needsApproval: "striped project-needsChanges",
    softDelete: "striped project-hidden"
};

function icon(faName) {
    return $('<i>').addClass(faName);
}

function setOwner(url, owner) {
    return url;
}

function makeState() {
    return {
        query: query,
        categories: categories,
        tags: tags,
        owner: owner,
        sort: sort,
        relevance: relevance,
        page: page
    };
}

function pushUrl(pageIncrement) {
    if (history) {
        var url = START_URL;

        function appendAnd() {
            if(url === START_URL) {
                url += "?"
            }
            else {
                url += "&"
            }
        }

        if(sort !== DEFAULT_SORT) {
            url += "?sort=" + sort;
        }

        if(!relevance) {
            appendAnd();
            url += "relevance=" + relevance;
        }

        var pageWithIncrement = page + pageIncrement;
        if(pageWithIncrement !== 1) {
            appendAnd();
            url += 'page=' + pageWithIncrement;
        }

        if (query) {
            appendAnd();
            url += 'q=' + query;
        }

        if (owner) {
            appendAnd();
            url = setOwner(url, owner);
        }

        for (var category of categories) {
            appendAnd();
            url += 'category=' + category;
        }

        if(tags.length >= 1) {
            appendAnd();
            url += 'platformName=' + tags[0];
        }

        history.pushState(makeState(), $(document).find("title").text(), url);
    }
}

window.onpopstate = function () {
    if (history.state) {
        query = history.state.query;
        categories = history.state.categories;
        tags = history.state.tags;
        owner = history.state.owner;
        sort = history.state.sort;
        relevance = history.state.relevance;
        page = history.state.page;

        $('#relevanceBox').prop('checked', relevance);
        $('#searchQuery').val(query);
        $('.select-sort').val(sort);
        //Page is set by loadProjects
        $('.category-list').children().each(function () {
            var apiName = $(this).data('apiname');
            if(!categories.includes(apiName)) {
                $(this).removeClass('active');
            }
        });

        if (categories.length !== 0) {
            $('#clearCategories').show();
        } else {
            $('#clearCategories').hide();
        }

        $('#platformCats').children().each(function () {
            var tagname = $(this).data('tagname');
            if(tagname !== 'any') {
                if(!tags.includes(tagname)) {
                    $(this).removeClass('active');
                }
            }
        });

        if(tags.length === 0) {
            $("a[data-tag='any']").addClass('active')
        }
        else {
            $("a[data-tag='any']").removeClass('active')
        }

        loadProjects(0, false, false);
    }
};

function loadProjects(increment, scrollTop, pushHistory) {
    var offset = (page + increment - 1) * PROJECTS_PER_PAGE;
    if(offset < 0) {
        offset = 0;
    }

    var url = 'projects' + '?sort=' + sort + '&relevance=' + relevance + '&limit=' + PROJECTS_PER_PAGE + '&offset=' + offset;
    if (query) {
        url = url + '&q=' + query;
    }

    if (owner) {
        url = url + '&owner=' + owner;
    }

    for (var category of categories) {
        url = url + '&categories=' + category;
    }

    for (var tag of tags) {
        url = url + '&tags=' + tag;
    }

    if(pushHistory) {
        pushUrl(increment);
    }
    apiV2Request(url).then(function (response) {
        var projectList = $('.project-list');
        projectList.empty();

        response.result.forEach(function (project) {
            var element = $("<li class='list-group-item project'>");
            projectList.append(element);

            var visibility = project.visibility;
            if (visibilityCssClasses[visibility]) {
                element.addClass(visibilityCssClasses[visibility]);
            }

            var container = $("<div class='container-fluid'>");
            element.append(container);

            var row = $("<div class='row'>");
            container.append(row);

            var iconCol = $("<div class='col-xs-12 col-sm-1'>");
            row.append(iconCol);

            var iconLink = $("<a>");
            iconLink.attr("href", '/' + project.namespace.owner);
            iconCol.append(iconLink);

            var iconImage = $("<img class='user-avatar user-avatar-sm'>");
            iconImage.attr("src", project.icon_url);
            iconLink.append(iconImage);

            var projectContent = $("<div class='col-xs-12 col-sm-11'>");
            row.append(projectContent);

            var infoRow = $("<div class='row'>");
            projectContent.append(infoRow);

            var titleCol = $("<div class='col-sm-6'>");
            infoRow.append(titleCol);

            var titleLink = $("<a class='title'>");
            titleLink.attr("href", '/' + project.namespace.owner + '/' + project.namespace.slug);
            titleLink.text(project.name);
            titleCol.append(titleLink);

            var infoCol = $("<div class='col-sm-6 hidden-xs'>");
            infoRow.append(infoCol);

            var info = $("<div class='info minor'>");
            infoCol.append(info);

            if (project.recommended_version) {
                var recommended = $("<span class='stat recommended-version' title='Recommended version'>");
                info.append(recommended);

                recommended.append(icon('far fa-gem'));

                var recommendedLink = $("<a>");
                recommendedLink.attr("href", '/' + project.namespace.owner + '/' + project.namespace.slug + '/versions/' + project.recommended_version.version);
                recommendedLink.text(project.recommended_version.version);
                recommended.append(recommendedLink);
            }

            function stat(title, faName, text) {
                var statSpan = $('<span class="stat">');
                statSpan.attr("title", title);
                statSpan.append(icon(faName));

                if (text !== undefined) {
                    statSpan.append(text);
                }

                return statSpan;
            }

            info.append(stat('Views', 'fas fa-eye', project.stats.views));
            info.append(stat('Downloads', 'fas fa-download', project.stats.downloads));
            info.append(stat('Stars', 'fas fa-star', project.stats.stars));
            info.append(stat(project.category, 'fas fa-star'));

            var textRow = $("<div class='row'>");
            projectContent.append(textRow);

            var descriptionCol = $("<div class='col-sm-7 description-column'>");
            textRow.append(descriptionCol);

            var description = $("<div class='description'>");
            descriptionCol.append(description);

            if (project.description) {
                description.text(project.description);
            } else {
                description.text("");
            }

            var tagLine = $("<div class='col-xs-12 col-sm-5 tags-line'>");
            textRow.append(tagLine);

            if (project.recommended_version) {
                for (var tag of project.recommended_version.tags) {
                    if (tag.name !== 'Channel') {
                        var tagContainer = $("<div class='tags'>");
                        if (tag.data) {
                            tagContainer.addClass("has-addons");
                        }

                        var tagElement = $("<span class='tag'>");
                        tagElement.text(tag.name);
                        tagElement.css("background", tag.color.background);
                        tagElement.css("border-color", tag.color.background);
                        tagElement.css("color", tag.color.foreground);
                        tagContainer.append(tagElement);

                        if (tag.data) {
                            var tagDataElement = $("<span class='tag'>");
                            tagDataElement.text(tag.data);
                            tagContainer.append(tagDataElement);
                        }

                        tagLine.append(tagContainer);
                    }
                }
            }
        });

        // Sets the new page number

        var totalProjects = response.pagination.count;

        if (!query && categories.length === 0 && tags.length === 0) {
            $('#searchQuery').attr("placeholder", 'Search in ' + totalProjects + ' projects, proudly made by the community...');
        }

        page += increment;

        var totalPages = Math.ceil(totalProjects / PROJECTS_PER_PAGE);

        function createPage(page) {
            var pageTemplate = $("<li>");
            pageTemplate.addClass("page");
            var link = $("<a>");
            link.text(page);
            pageTemplate.append(link);

            return pageTemplate;
        }

        var pagination = $(".pagination");

        if (totalPages > 1) {

            // Sets up the pagination
            pagination.empty();

            var prev = $("<li>");
            prev.addClass("prev");
            if (page === 1) {
                prev.addClass("disabled");
            }
            prev.append("<a>&laquo;</a>");
            pagination.append(prev);

            var left = totalPages - page;

            // Dot Template
            var dotTemplate = $("<li>");
            dotTemplate.addClass("disabled");
            var dotLink = $("<a>");
            dotLink.text("...");
            dotTemplate.append(dotLink);

            // [First] ...
            if (totalPages > 3 && page >= 3) {
                pagination.append(createPage(1));

                if (page > 3) {
                    pagination.append(dotTemplate);
                }
            }

            //=> [current - 1] [current] [current + 1] logic
            if (totalPages > 2) {
                if (left === 0) {
                    pagination.append(createPage((totalPages - 2)))
                }
            }

            if (page !== 1) {
                pagination.append(createPage((page - 1)))
            }

            var activePage = $("<li>");
            activePage.addClass("page active");
            var link = $("<a>");
            link.text(page);
            activePage.append(link);
            pagination.append(activePage);


            if ((page + 1) <= totalPages) {
                pagination.append(createPage(page + 1))
            }

            if (totalPages > 2) {
                if (page === 1) {
                    pagination.append(createPage(page + 2)) // Adds a third page if current page is first page
                }
            }

            // [Last] ...
            if (totalPages > 3 && left > 1) {
                if (left > 2) {
                    pagination.append(dotTemplate.clone());
                }

                pagination.append(createPage(totalPages));
            }

            // Builds the pagination

            var next = $("<li>");
            next.addClass("next");
            if (totalProjects / PROJECTS_PER_PAGE <= page) {
                next.addClass("disabled");
            }
            next.append("<a>&raquo;</a>");

            pagination.append(next);

            // Prev & Next Buttons
            pagination.find('.next').click(function () {
                if (totalProjects / PROJECTS_PER_PAGE > page) {
                    loadProjects(1, true, true);
                }
            });

            pagination.find('.prev').click(function () {
                if (page > 1) {
                    loadProjects(-1, true, true)
                }
            });

            pagination.find('.page').click(function () {
                var toPage = Number.parseInt($(this).text());

                if (!isNaN(toPage)) {
                    loadProjects(toPage - page, true, true);
                }
            });
        } else {
            pagination.empty();
        }

        pagination.show();

        $(".loading").hide();
        projectList.show();

        if (scrollTop === true) {
            $("html, body").animate({scrollTop: $('.project-list').offset().top - 130}, 250);
        }
    })
}

function resetLoadProjects() {
    page = 1;
    loadProjects(0, false, true);
}

//https://davidwalsh.name/javascript-debounce-function
function debounce(wait, f, immediate) {
    var timeout;
    return function () {
        var context = this, args = arguments;
        var later = function () {
            timeout = null;
            if (!immediate) f.apply(context, args);
        };
        var callNow = immediate && !timeout;
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
        if (callNow) f.apply(context, args);
    };
}

$(function () {
    if (history) {
        history.replaceState(makeState(), $(document).find("title").text())
    }

    loadProjects(0, false, false);

    $('#relevanceBox').on('change', function () {
        var newRelevance = $(this).is(":checked");

        if (newRelevance !== relevance) {
            relevance = newRelevance;
            resetLoadProjects();
        }
    });

    $('.select-sort').on('change', function () {
        var newSort = $(this).val();

        if (newSort !== sort) {
            sort = newSort;
            resetLoadProjects();
        }
    });

    $('#searchQuery').on('change paste keyup', debounce(250, function () {
        var newQuery = $(this).val();

        if (newQuery !== query) {
            query = newQuery;
            resetLoadProjects();
        }
    }));

    $('#clearCategories').on('click', function () {
        $('.category-list').children().removeClass('active');
        categories = [];
        resetLoadProjects();
        $(this).hide();
    });

    $('.category-list').children().on('click', function () {
        var apiName = $(this).data('apiname');
        $(this).toggleClass('active');

        if (categories.includes(apiName)) {
            var idx = categories.indexOf(apiName);
            categories.splice(idx, 1)
        } else {
            categories.push(apiName);
        }

        if (categories.length !== 0) {
            $('#clearCategories').show();
        } else {
            $('#clearCategories').hide();
        }

        resetLoadProjects();
    });

    $('#platformCats').children().on('click', function () {
        var tagname = $(this).data('tagname');
        if (tagname === 'any') {
            tags = []
        } else {
            tags = [tagname];
        }
        $('#platformCats').children().removeClass('active');
        $(this).addClass('active');

        resetLoadProjects();
    });
});