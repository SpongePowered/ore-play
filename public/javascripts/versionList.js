/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Handles async loading and display of the version list.
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var PLUGIN_ID = null;
var VERSIONS_PER_PAGE = 10;
var PROJECT_OWNER = null;
var PROJECT_SLUG = null;
var TEXT_NOT_APPROVED = "";
var TEXT_PARTIALLY_APPROVED = "";
var TEXT_NOT_APPROVED_CHANNEL = "";
var SHOW_HIDDEN = false;

var channels = [];
var page = 0;

var visibilityCssClasses = {
    new: "project-new",
    needsChanges: "striped project-needsChanges",
    needsApproval: "striped project-needsChanges",
    softDelete: "striped project-hidden"
};

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function createPage(page) {
    var pageTemplate = $("<li>");
    pageTemplate.addClass("page");
    var link = $("<a>");
    link.text(page);
    pageTemplate.append(link);

    return pageTemplate;
}

function loadVersions(increment, scrollTop) {
    var versionList = $('.version-table');

    var offset = (page + increment - 1) * VERSIONS_PER_PAGE;
    var url = 'projects/' + PLUGIN_ID + '/versions' + '?offset=' + offset;
    for (var urlChannel of channels) {
        url += '&tags=Channel:' + urlChannel
    }

    apiV2Request(url).then(function (response) {
        var versionTable = $(".version-table tbody");
        versionTable.empty();

        response.result.forEach(function (version) {
            var row = $("<tr>");

            var visibility = version.visibility;
            if(visibilityCssClasses[visibility]) {
                row.addClass(visibilityCssClasses[visibility])
            }

            // ==> Base Info (channel, name)
            var baseInfo = $("<td>");
            baseInfo.addClass("base-info");

            var nameElement = $("<div>");
            nameElement.addClass("name");
            var nameLink = $("<a>");
            nameLink.text(version.name);
            var href = projectOwner + '/' + projectSlug + '/versions/' + version.name;
            nameLink.attr("href", href);
            nameElement.append(nameLink);
            baseInfo.append(nameElement);

            for (var tag of version.tags) {
                if(tag.name === 'Channel') {
                    var channel = tag.data;
                    var channelElement = $("<span>");
                    channelElement.addClass("channel");
                    channelElement.text(channel);
                    channelElement.css("background", tag.color.background);
                    baseInfo.append(channelElement);
                }
            }

            row.append(baseInfo);

            // => Tags

            var tags = $("<td>");
            tags.addClass("version-tags");
            version.tags.forEach(function (tag) {
                if(tag.name !== 'Channel') {
                    var tagContainer = $("<div>");
                    tagContainer.addClass("tags");
                    if(tag.data) {
                        tagContainer.addClass("has-addons");
                    }

                    var tagElement = $("<span>");
                    tagElement.addClass("tag");
                    tagElement.text(tag.name);
                    tagElement.css("background", tag.color.background);
                    tagElement.css("border-color", tag.color.background);
                    tagElement.css("color", tag.color.foreground);
                    tagContainer.append(tagElement);

                    if(tag.data) {
                        var tagDataElement = $("<span>");
                        tagDataElement.addClass("tag");
                        tagDataElement.text(tag.data);
                        tagContainer.append(tagDataElement);
                    }

                    tags.append(tagContainer);
                }
            });

            row.append(tags);

            // => Information One (created, size)

            var infoOne = $("<td>");
            infoOne.addClass("information-one");

            var createdContainer = $("<div>");
            createdContainer.append("<i class='fas fa-calendar'></i>");

            var created = $("<span>");
            created.text(moment(version.created_at).format("MMM D, YYYY"));
            createdContainer.append(created);

            infoOne.append(createdContainer);


            var sizeContainer = $("<div>");
            sizeContainer.append("<i class='far fa-file'></i>");

            var size = $("<span>");
            size.text(filesize(version.file_info.size_bytes));
            sizeContainer.append(size);

            infoOne.append(sizeContainer);
            row.append(infoOne);

            // => Information Two (author, download count)

            var infoTwo = $("<td>");
            infoTwo.addClass("information-two");

            if(version.author) {
                var authorContainer = $("<div>");
                authorContainer.addClass("author");
                authorContainer.append("<i class='fas fa-key'></i>");

                var author = $("<span>");
                author.text(version.author);
                author.attr("title", "This version is signed by " + version.author);
                author.attr("data-toggle", "tooltip");
                author.attr("data-placement", "bottom");

                authorContainer.append(author);
                infoTwo.append(authorContainer);
            }

            var downloadContainer = $("<div>");
            downloadContainer.append("<i class='fas fa-download'></i>");
            var downloads = $("<span>");
            downloads.text(version.stats.downloads + " Downloads");
            downloadContainer.append(downloads);
            infoTwo.append(downloadContainer);

            row.append(infoTwo);

            // => Download

            var download = $("<td>");
            download.addClass("download");

            var downloadLink = $("<a>");
            downloadLink.addClass("download-link");
            downloadLink.attr('href', href +  '/download/');

            downloadLink.append("<i class='fas fa-2x fa-download'></i>");

            if(version.review_state !== "reviewed") {
                var text;
                if (channel && channel.nonReviewed) {
                    text = TEXT_NOT_APPROVED_CHANNEL;
                }
                else if (version.reviewState === "partially_reviewed") {
                    text = TEXT_PARTIALLY_APPROVED;
                }
                else {
                    text = TEXT_NOT_APPROVED;
                }

                var warning = $("<i>");
                warning.attr("title", text);
                warning.attr("data-toggle", "tooltip");
                warning.attr("data-placement", "bottom");

                if(version.reviewState === "partially_reviewed") {
                    warning.addClass("fas fa-check");
                }
                else {
                    warning.addClass("fas fa-exclamation-circle");
                }

                downloadLink.append(warning);
            }

            download.append(downloadLink);
            row.append(download);

            versionTable.append(row);
        });

        // Sets the new page number

        var totalVersions = response.pagination.count;

        page += increment;

        var totalPages = Math.ceil(totalVersions / VERSIONS_PER_PAGE);

        if(totalPages > 1) {

            // Sets up the pagination
            var pagination = $(".version-panel .pagination");
            pagination.empty();

            var prev = $("<li>");
            prev.addClass("prev");
            if(page === 1) {
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
            if(totalPages > 3 && page >= 3) {
                pagination.append(createPage(1));

                if(page > 3) {
                    pagination.append(dotTemplate);
                }
            }

            //=> [current - 1] [current] [current + 1] logic
            if(totalPages > 2) {
                if(left === 0) {
                    pagination.append(createPage((totalPages - 2)))
                }
            }

            if(page !== 1) {
                pagination.append(createPage((page -1)))
            }

            var activePage = $("<li>");
            activePage.addClass("page active");
            var link = $("<a>");
            link.text(page);
            activePage.append(link);
            pagination.append(activePage);


            if((page + 1) <= totalPages) {
                pagination.append(createPage(page + 1))
            }

            if(totalPages > 2) {
                if(page === 1) {
                    pagination.append(createPage(page + 2)) // Adds a third page if current page is first page
                }
            }

            // [Last] ...
            if(totalPages > 3 && left > 1) {
                if(left > 2) {
                    pagination.append(dotTemplate.clone());
                }

                pagination.append(createPage(totalPages));
            }

            // Builds the pagination

            var next = $("<li>");
            next.addClass("next");
            if(totalVersions / VERSIONS_PER_PAGE <= page) {
                next.addClass("disabled");
            }
            next.append("<a>&raquo;</a>");

            pagination.append(next);

            // Prev & Next Buttons
            pagination.find('.next').click(function () {
                if (totalVersions / VERSIONS_PER_PAGE > page) {
                    loadVersions(1, true);
                }
            });

            pagination.find('.prev').click(function () {
                if (page > 1) {
                    loadVersions(-1, true)
                }
            });

            pagination.find('.page').click(function () {
                var toPage = Number.parseInt($(this).text());

                if(!isNaN(toPage)) {
                    loadVersions(toPage - page, true);
                }
            });
        }
        else {
            $(".version-panel .pagination").empty();
        }

        $(".panel-pagination").show();

        // Sets tooltips up
        $('.version-list [data-toggle="tooltip"]').tooltip({
            container: 'body'
        });

        $(".loading").hide();
        versionList.show();

        if(scrollTop === true) {
            $("html, body").animate({ scrollTop: $('.version-table').offset().top - 130 }, 250);
        }
    });
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function () {
    loadVersions(1, false);

    // Setup channel list

    $('.list-channel').find('li').find('input').on('change', function () {
        var channelName = $(this).closest('.list-group-item').find('.channel').text();
        if ($(this).is(":checked")) {
            channels.push(channelName);
        } else {
            channels = [];
            var checked = $('.list-channel').find('li').find('input:checked');
            checked.each(function (i) {
                channels.push($(this).closest('.list-group-item').find('.channel').text());
            });
        }

        page = 0;
        loadVersions(1, false);
    });

    $('.channels-all').on('change', function () {
        channels = [];
        if (!$(this).is(":checked")) {
            var checked = $('.list-channel').find('li').find('input');
            checked.each(function () {
                channels.push($(this).closest('.list-group-item').find('.channel').text());
            });
        }

        page = 0;
        loadVersions(1, false);
    });
});
