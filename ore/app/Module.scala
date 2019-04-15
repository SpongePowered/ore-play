import db.impl.service.OreModelService
import discourse.{OreDiscourseApi, SpongeForums}
import mail.{Mailer, SpongeMailer}
import ore._
import ore.db.ModelService
import ore.markdown.{FlexmarkRenderer, MarkdownRenderer}
import ore.project.factory.{OreProjectFactory, ProjectFactory}
import ore.rest.{OreRestfulApiV1, OreRestfulServerV1}
import security.spauth.{SingleSignOnConsumer, SpongeAuth, SpongeAuthApi, SpongeSingleSignOnConsumer}

import com.google.inject.AbstractModule

/** The Ore Module */
class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MarkdownRenderer]).to(classOf[FlexmarkRenderer])
    bind(classOf[OreRestfulApiV1]).to(classOf[OreRestfulServerV1])
    bind(classOf[StatTracker]).to(classOf[OreStatTracker])
    bind(classOf[ProjectFactory]).to(classOf[OreProjectFactory])
    bind(classOf[OreDiscourseApi]).to(classOf[SpongeForums])
    bind(classOf[SpongeAuthApi]).to(classOf[SpongeAuth])
    bind(classOf[ModelService]).to(classOf[OreModelService])
    bind(classOf[Mailer]).to(classOf[SpongeMailer])
    bind(classOf[SingleSignOnConsumer]).to(classOf[SpongeSingleSignOnConsumer])
    bind(classOf[Bootstrap]).to(classOf[BootstrapImpl]).asEagerSingleton()
  }

}
